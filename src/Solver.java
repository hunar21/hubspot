import Ojbects.*;
import java.time.*;
import java.util.*;

public class Solver {

    private static final ZoneId UTC = ZoneOffset.UTC;

    static List<ResultEntry> computeResults(CallRecord[] calls) {
        // Step 1: Convert call records into daily event streams
        Map<Integer, Map<LocalDate, List<Event>>> dailyEvents = buildDailyEvents(calls);

        // Step 2: Reduce events into max concurrency results
        return reduceToResults(dailyEvents);
    }


    private static Map<Integer, Map<LocalDate, List<Event>>> buildDailyEvents(CallRecord[] calls) {
        Map<Integer, Map<LocalDate, List<Event>>> grouped = new HashMap<>();

        for (CallRecord c : calls) {
            // Calls can span multiple days → find first and last UTC date
            LocalDate first = Instant.ofEpochMilli(c.startTimestamp()).atZone(UTC).toLocalDate();
            LocalDate last  = Instant.ofEpochMilli(c.endTimestamp() - 1).atZone(UTC).toLocalDate();

            // For each day that the call overlaps:
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                long dayStart = d.atStartOfDay(UTC).toInstant().toEpochMilli();
                long dayEnd   = d.plusDays(1).atStartOfDay(UTC).toInstant().toEpochMilli();

                // Clip call to fit within [dayStart, dayEnd)
                long segStart = Math.max(c.startTimestamp(), dayStart); // inclusive
                long segEnd   = Math.min(c.endTimestamp(),   dayEnd);   // exclusive

                if (segStart < segEnd) {
                    // Add START and END events for this day segment
                    addSegmentEvents(grouped, c.customerId(), d, segStart, segEnd, c.callId(), c.endTimestamp());
                }
            }
        }
        return grouped;
    }

    /**
     * Reduce daily events into ResultEntry objects:
     * Sort events, sweep line to track active calls,
     * record the maximum concurrency and corresponding callIds.
     */
    private static List<ResultEntry> reduceToResults(Map<Integer, Map<LocalDate, List<Event>>> dailyEvents) {
        List<ResultEntry> out = new ArrayList<>();

        for (var byCustomer : dailyEvents.entrySet()) {
            int customerId = byCustomer.getKey();

            for (var byDate : byCustomer.getValue().entrySet()) {
                LocalDate date = byDate.getKey();
                List<Event> events = byDate.getValue();

                // Sort: END first if same timestamp (because end is exclusive)
                events.sort(
                        Comparator.comparingLong(Event::time)
                                .thenComparing(e -> e.type, Comparator.comparingInt(t -> t == Type.END ? 0 : 1))
                                .thenComparing(Event::callId)
                );

                // Compute the maximum concurrency for this (customer, date)
                ResultEntry entry = computeDayMax(customerId, date, events);
                if (entry != null) out.add(entry);
            }
        }
        return out;
    }

    /* ===== Helpers ===== */

    /**
     * Add both START and END events for a call segment that fits within one day.
     * END is added first (at segEnd) to enforce end-exclusive rule.
     */
    private static void addSegmentEvents(Map<Integer, Map<LocalDate, List<Event>>> grouped,
                                         int customerId,
                                         LocalDate date,
                                         long segStart,
                                         long segEnd,
                                         String callId,
                                         long originalEnd) {
        List<Event> bucket = grouped
                .computeIfAbsent(customerId, k -> new HashMap<>())
                .computeIfAbsent(date,      k -> new ArrayList<>());

        bucket.add(new Event(segEnd,   Type.END,   callId, originalEnd));   // END event
        bucket.add(new Event(segStart, Type.START, callId, originalEnd));   // START event
    }


    /**
     * Perform sweep line algorithm for one day's events.
     * Tracks currently active calls, updates maximum concurrent set.
     */
    private static ResultEntry computeDayMax(int customerId, LocalDate date, List<Event> events) {
        if (events.isEmpty()) return null;

        Map<String, Long> active = new LinkedHashMap<>(); // callId -> originalEnd
        int max = 0;
        long bestTs = 0L;
        List<String> bestIds = List.of();

        int i = 0;
        while (i < events.size()) {
            long t = events.get(i).time;

            // 1) process all END events at this timestamp
            int j = i;
            while (j < events.size() && events.get(j).time == t && events.get(j).type == Type.END) {
                active.remove(events.get(j).callId);
                j++;
            }

            // 2) process all START events at this timestamp
            int k = j;
            while (k < events.size() && events.get(k).time == t && events.get(k).type == Type.START) {
                active.put(events.get(k).callId, events.get(k).originalEnd);
                k++;
            }

            // After applying ENDs then STARTs, check concurrency
            if (active.size() > max) {
                max = active.size();
                bestTs = t;
                bestIds = new ArrayList<>(active.keySet()); // snapshot of active calls
            }

            i = k; // advance to next timestamp block
        }

        if (max == 0) return null;

        return new ResultEntry(
                customerId,
                date.toString(),   // format YYYY-MM-DD
                max,
                bestTs,
                bestIds
        );
    }

    /* ===== Internal helper types ===== */

    private enum Type { START, END }

    /**
     * Represents a start or end event for one call, clipped to a given day.
     */
    private static final class Event {
        final long time;        // epoch millis
        final Type type;        // START or END
        final String callId;    // which call this event belongs to
        final long originalEnd; // full original end timestamp

        Event(long time, Type type, String callId, long originalEnd) {
            this.time = time;
            this.type = type;
            this.callId = callId;
            this.originalEnd = originalEnd;
        }

        long time()   { return time; }
        String callId(){ return callId; }
    }
}
