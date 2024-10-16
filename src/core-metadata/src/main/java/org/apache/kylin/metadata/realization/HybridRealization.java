/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.metadata.realization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.KylinConfigExt;
import org.apache.kylin.guava30.shaded.common.collect.Lists;
import org.apache.kylin.metadata.model.FunctionDesc;
import org.apache.kylin.metadata.model.IStorageAware;
import org.apache.kylin.metadata.model.MeasureDesc;
import org.apache.kylin.metadata.model.NDataModel;
import org.apache.kylin.metadata.model.NDataModelManager;
import org.apache.kylin.metadata.model.TblColRef;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HybridRealization implements IRealization {

    public static final String REALIZATION_TYPE = "HYBRID";

    @Getter
    private String uuid;
    private final List<IRealization> realizations = new ArrayList<>();
    private volatile IRealization batchRealization;
    private volatile IRealization streamingRealization;
    private String project;

    private List<TblColRef> allDimensions = null;
    private Set<TblColRef> allColumns = null;
    private List<MeasureDesc> allMeasures = null;
    private long dateRangeStart;
    private long dateRangeEnd;
    private boolean isReady = false;
    private KylinConfigExt config;

    public HybridRealization(IRealization batchRealization, IRealization streamingRealization, String project) {
        if (batchRealization == null || streamingRealization == null) {
            return;
        }
        this.batchRealization = batchRealization;
        this.streamingRealization = streamingRealization;
        this.realizations.add(batchRealization);
        this.realizations.add(streamingRealization);
        this.project = project;

        LinkedHashSet<TblColRef> columns = new LinkedHashSet<>();
        LinkedHashSet<TblColRef> dimensions = new LinkedHashSet<>();
        allMeasures = Lists.newArrayList();
        dateRangeStart = 0;
        dateRangeEnd = Long.MAX_VALUE;
        for (IRealization realization : realizations) {
            columns.addAll(realization.getAllColumns());
            dimensions.addAll(realization.getAllDimensions());
            allMeasures.addAll(realization.getMeasures());
            if (realization.isOnline())
                isReady = true;

            if (dateRangeStart == 0 || realization.getDateRangeStart() < dateRangeStart)
                dateRangeStart = realization.getDateRangeStart();

            if (dateRangeStart == Long.MAX_VALUE || realization.getDateRangeEnd() > dateRangeEnd)
                dateRangeEnd = realization.getDateRangeEnd();
        }

        if (streamingRealization.getMeasures().isEmpty()) {
            allMeasures.addAll(streamingRealization.getModel().getAllMeasures());
        }

        allDimensions = Lists.newArrayList(dimensions);
        allColumns = columns;
        uuid = streamingRealization.getUuid();
        sortRealizations();
    }

    private void sortRealizations() {
        realizations.sort((realization1, realization2) -> {
            long dateRangeStart1 = realization1.getDateRangeStart();
            long dateRangeStart2 = realization2.getDateRangeStart();
            long comp = dateRangeStart1 - dateRangeStart2;
            if (comp != 0) {
                return comp > 0 ? 1 : -1;
            }

            dateRangeStart1 = realization1.getDateRangeEnd();
            dateRangeStart2 = realization2.getDateRangeEnd();
            comp = dateRangeStart1 - dateRangeStart2;
            if (comp != 0) {
                return comp > 0 ? 1 : -1;
            }

            return 0;
        });
    }

    @Override
    public int getCost() {
        int c = Integer.MAX_VALUE;
        for (IRealization realization : getRealizations()) {
            c = Math.min(realization.getCost(), c);
        }
        // let hybrid cost win its children
        return --c;
    }

    public List<IRealization> getRealizations() {
        return realizations;
    }

    @Override
    public FunctionDesc findAggrFunc(FunctionDesc aggrFunc) {
        for (MeasureDesc measure : this.getMeasures()) {
            if (measure.getFunction().equals(aggrFunc))
                return measure.getFunction();
        }
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        if (aggrFunc.isCountOnColumn() && kylinConfig.isReplaceColCountWithCountStar()) {
            return FunctionDesc.newCountOne();
        }
        return aggrFunc;
    }

    public IRealization getBatchRealization() {
        return batchRealization;
    }

    public IRealization getStreamingRealization() {
        return streamingRealization;
    }

    @Override
    public String getType() {
        return REALIZATION_TYPE;
    }

    @Override
    public KylinConfigExt getConfig() {
        return config;
    }

    public void setConfig(KylinConfigExt config) {
        this.config = config;
    }

    @Override
    public NDataModel getModel() {
        return NDataModelManager.getInstance(KylinConfig.getInstanceFromEnv(), project).getDataModelDesc(uuid);
    }

    @Override
    public Set<TblColRef> getAllColumns() {
        return allColumns;
    }

    @Override
    public List<MeasureDesc> getMeasures() {
        return allMeasures;
    }

    @Override
    public List<TblColRef> getAllDimensions() {
        return allDimensions;
    }

    @Override
    public boolean isOnline() {
        return isReady;
    }

    @Override
    public String getCanonicalName() {
        return getType() + "[name=" + getModel().getAlias() + "]";
    }

    @Override
    public long getDateRangeStart() {
        return dateRangeStart;
    }

    @Override
    public long getDateRangeEnd() {
        return dateRangeEnd;
    }

    @Override
    public boolean hasPrecalculatedFields() {
        return true;
    }

    @Override
    public int getStorageType() {
        return IStorageAware.ID_NDATA_STORAGE;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setProject(String project) {
        this.project = project;
    }

    @Override
    public String getProject() {
        return this.project;
    }

    @Override
    public boolean isStreaming() {
        return false;
    }
}
