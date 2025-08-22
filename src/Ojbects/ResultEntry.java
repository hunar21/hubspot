package Ojbects;

import java.util.List;

public record ResultEntry(
        int customerId,
        String date,              // YYYY-MM-DD (UTC)
        int maxConcurrentCalls,
        long timestamp,           // a time when the max was reached
        List<String> callIds      // the call IDs active at 'timestamp'
) {}
