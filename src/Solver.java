import Ojbects.*;
import java.time.*;
import java.util.*;

public class Solver {
    private static final ZoneId UTC = ZoneOffset.UTC;

    static List<ResultEntry> computeResults(List<Records> records) {
        Map<Integer, Map<LocalDate, List<Event>>> map = buildEventLists(records);

        List<ResultEntry> result = new ArrayList<>();
        for (var cEntry : map.entrySet()) {
            for (var dEntry : cEntry.getValue().entrySet()) {
                ResultEntry r = reduceDay(cEntry.getKey(), dEntry.getKey(), dEntry.getValue());
                result.add(r);
            }
        }
        return result;
    }

    private static Map<Integer, Map<LocalDate, List<Event>>> buildEventLists(List<Records> records) {
        Map<Integer, Map<LocalDate, List<Event>>> map = new HashMap<>();

        for (Records r : records) {
            // figure out which UTC days this call touches (use end-1ms so midnight falls on the previous day)
            LocalDate first = Instant.ofEpochMilli(r.startTimestamp()).atZone(UTC).toLocalDate();
            LocalDate last  = Instant.ofEpochMilli(r.endTimestamp() - 1).atZone(UTC).toLocalDate();

            for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
                long dayStart = date.atStartOfDay(UTC).toInstant().toEpochMilli();
                long dayEnd   = date.plusDays(1).atStartOfDay(UTC).toInstant().toEpochMilli();

                // clip to [dayStart, dayEnd) half-open window
                long s = Math.max(r.startTimestamp(), dayStart);
                long e = Math.min(r.endTimestamp(),   dayEnd);

                if (s < e) {
                    List<Event> events = map
                            .computeIfAbsent(r.customerId(), k -> new HashMap<>())
                            .computeIfAbsent(date,             k -> new ArrayList<>());

                    // add START and END events for that day segment
                    events.add(new Event(s, true,  r.callId()));  // START
                    events.add(new Event(e, false, r.callId()));  // END (end-exclusive)
                }
            }
        }
        return map;
    }

    private static ResultEntry reduceDay(int customerId, LocalDate date, List<Event> events) {
        events.sort(
                Comparator.comparingLong(Event::time)
                        .thenComparing(e -> e.start() ? 1 : 0)   // END(0) before START(1)
        );

        Set<String> active = new LinkedHashSet<>(); // stable ordering for snapshots
        int max = 0;
        long bestTs = 0L;
        List<String> bestIds = Collections.emptyList();

        int i = 0;
        while (i < events.size()) {
            long t = events.get(i).time();
            if(events.get(i).start)active.add(events.get(i).callId);
            else active.remove(events.get(i).callId);

            if (active.size() > max) {
                max = active.size();
                bestTs = t;
                bestIds = new ArrayList<>(active); // snapshot
            }

            i++; // advance to next timestamp block
        }

        if (max == 0) return null;

        return new ResultEntry(
                customerId,
                date.toString(),  // YYYY-MM-DD
                max,
                bestTs,
                bestIds
        );
    }
    private record Event(long time, boolean start, String callId) {}
}
