package Ojbects;

public record CallRecord(
        int customerId,
        String callId,
        long startTimestamp, // inclusive
        long endTimestamp    // exclusive
) {}
