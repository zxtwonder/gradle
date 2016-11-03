/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.measure

import spock.lang.Specification

class DataSeriesTest extends Specification {
    def "can calculate statistics for samples"() {
        def min = DataAmount.kbytes(1)
        def v1 = DataAmount.kbytes(10)
        def v2 = DataAmount.kbytes(20)
        def v3 = DataAmount.kbytes(30)
        def max = DataAmount.kbytes(99)
        def series = new DataSeries([min, v1, v2, v3, max])

        expect:
        series.average == v2
        series.min == v1
        series.max == v3
        series.standardError == DataAmount.bytes(8360.92)
        series.standardErrorOfMean == DataAmount.bytes(4827.179413)
    }

    def "ignores null values"() {
        def min = DataAmount.kbytes(1)
        def v1 = DataAmount.kbytes(10)
        def v2 = DataAmount.kbytes(20)
        def v3 = DataAmount.kbytes(30)
        def max = DataAmount.kbytes(99)
        def series = new DataSeries([v1, v2, null, v3, null, min, max])

        expect:
        series.size() == 5
        series.average == v2
        series.min == v1
        series.max == v3
    }

    def "can be empty"() {
        def series = new DataSeries([null, null])

        expect:
        series.empty
        series.average == null
        series.min == null
        series.max == null
        series.standardError == null
        series.standardErrorOfMean == null
    }
}
