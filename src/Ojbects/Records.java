package Ojbects;

public record Records(
        int customerId,
        String callId,
        long startTimestamp, // inclusive
        long endTimestamp    // exclusive
) {}
