--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
with avg_tmp as (
    select
        avg(c_acctbal) as avg_acctbal
    from
        customer
    where
        c_acctbal > 0.00 and substring(c_phone, 1, 2) in ('13','31','23','29','30','18','17')
),
cus_tmp as (
    select c_custkey as noordercus
    from
        customer left join v_orders on c_custkey = o_custkey
    where o_orderkey is null
)

select
    cntrycode,
    count(1) as numcust,
    sum(c_acctbal) as totacctbal
from (
    select
        substring(c_phone, 1, 2) as cntrycode,
        c_acctbal
    from
        customer inner join cus_tmp on c_custkey = noordercus, avg_tmp
    where
        substring(c_phone, 1, 2) in ('13','31','23','29','30','18','17')
        and c_acctbal > avg_acctbal
) t
group by
    cntrycode
order by
    cntrycode;
