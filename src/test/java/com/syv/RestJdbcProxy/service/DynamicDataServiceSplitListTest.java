package com.syv.RestJdbcProxy.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicDataServiceSplitListTest {

    private final DynamicDataService dynamicDataService = new DynamicDataService();

    @Test
    void returnsNoPartitionsForEmptyInput() {
        List<List<Map<String, Object>>> partitions = dynamicDataService
                .splitList2sublists(List.of(), 10)
                .toList();

        assertEquals(List.of(), partitions);
    }

    @Test
    void treatsNonPositiveThreadCountAsOnePartition() {
        List<List<Map<String, Object>>> partitions = dynamicDataService
                .splitList2sublists(items(3), 0)
                .toList();

        assertPartitionSizes(partitions, 3);
    }

    @Test
    void capsPartitionCountAtListSize() {
        List<List<Map<String, Object>>> partitions = dynamicDataService
                .splitList2sublists(items(3), 10)
                .toList();

        assertPartitionSizes(partitions, 1, 1, 1);
    }

    @Test
    void distributesRemainderAcrossExistingPartitions() {
        List<List<Map<String, Object>>> partitions = dynamicDataService
                .splitList2sublists(items(10), 3)
                .toList();

        assertPartitionSizes(partitions, 4, 3, 3);
    }

    @Test
    void distributesRemainderAcrossFourPartitions() {
        List<List<Map<String, Object>>> partitions = dynamicDataService
                .splitList2sublists(items(10), 4)
                .toList();

        assertPartitionSizes(partitions, 3, 3, 2, 2);
    }

    private static List<Map<String, Object>> items(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of("id", i))
                .toList();
    }

    private static void assertPartitionSizes(List<List<Map<String, Object>>> partitions, int... expectedSizes) {
        List<Integer> actualSizes = partitions.stream()
                .map(List::size)
                .toList();
        List<Integer> expected = IntStream.of(expectedSizes)
                .boxed()
                .toList();

        assertEquals(expected, actualSizes);
    }
}
