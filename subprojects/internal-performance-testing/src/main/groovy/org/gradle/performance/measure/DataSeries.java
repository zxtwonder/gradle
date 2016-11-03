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

package org.gradle.performance.measure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A collection of measurements of some given units.
 */
public class DataSeries<Q> extends ArrayList<Amount<Q>> {
    private final Amount<Q> average;
    private final Amount<Q> max;
    private final Amount<Q> min;
    // https://en.wikipedia.org/wiki/Standard_error
    private final Amount<Q> standardError;
    // https://en.wikipedia.org/wiki/Standard_error#Standard_error_of_the_mean
    private final Amount<Q> standardErrorOfMean;

    public DataSeries(Iterable<? extends Amount<Q>> values) {
        for (Amount<Q> value : values) {
            if (value != null) {
                add(value);
            }
        }

        if (isEmpty()) {
            average = null;
            max = null;
            min = null;
            standardError = null;
            standardErrorOfMean = null;
            return;
        }

        Set<Integer> excludeIndexes;
        if (size() > 2) {
            excludeIndexes = resolveIndexesToExcludeFromCalculations();
        } else {
            excludeIndexes = Collections.emptySet();
        }

        Amount<Q> total = null;
        Amount<Q> min = null;
        Amount<Q> max = null;
        int numberOfEntries = 0;
        for (int i = 0; i < size(); i++) {
            if (!excludeIndexes.contains(i)) {
                Amount<Q> amount = get(i);
                if (numberOfEntries == 0) {
                    total = amount;
                    min = amount;
                    max = amount;
                } else {
                    total = total.plus(amount);
                    min = min.compareTo(amount) <= 0 ? min : amount;
                    max = max.compareTo(amount) >= 0 ? max : amount;
                }
                numberOfEntries++;
            }
        }
        average = total.div(numberOfEntries);
        this.min = min;
        this.max = max;

        BigDecimal sumSquares = BigDecimal.ZERO;
        Units<Q> baseUnits = average.getUnits().getBaseUnits();
        BigDecimal averageValue = average.toUnits(baseUnits).getValue();
        for (int i = 0; i < size(); i++) {
            if (!excludeIndexes.contains(i)) {
                Amount<Q> amount = get(i);
                BigDecimal diff = amount.toUnits(baseUnits).getValue();
                diff = diff.subtract(averageValue);
                diff = diff.multiply(diff);
                sumSquares = sumSquares.add(diff);
            }
        }
        // This isn't quite right, as we may lose precision when converting to a double
        BigDecimal result = BigDecimal.valueOf(Math.sqrt(sumSquares.divide(BigDecimal.valueOf(numberOfEntries), BigDecimal.ROUND_HALF_UP).doubleValue())).setScale(2, BigDecimal.ROUND_HALF_UP);

        standardError = Amount.valueOf(result, baseUnits);
        standardErrorOfMean = standardError.div(BigDecimal.valueOf(Math.sqrt(numberOfEntries)));
    }

    private Set<Integer> resolveIndexesToExcludeFromCalculations() {
        Set<Integer> excludeIndexes = new HashSet<Integer>();
        int minIndex = 0;
        int maxIndex = 0;
        Amount<Q> min = get(0);
        Amount<Q> max = get(0);
        for (int i = 1; i < size(); i++) {
            Amount<Q> amount = get(i);
            if (min.compareTo(amount) > 0) {
                min = amount;
                minIndex = i;
            }
            if (max.compareTo(amount) < 0) {
                max = amount;
                maxIndex = i;
            }
        }
        excludeIndexes.add(minIndex);
        excludeIndexes.add(maxIndex);
        return excludeIndexes;
    }


    public Amount<Q> getAverage() {
        return average;
    }

    public Amount<Q> getMin() {
        return min;
    }

    public Amount<Q> getMax() {
        return max;
    }

    public Amount<Q> getStandardError() {
        return standardError;
    }

    public Amount<Q> getStandardErrorOfMean() {
        return standardErrorOfMean;
    }
}
