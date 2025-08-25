import Ojbects.*;
import java.time.*;
import java.util.*;

public class Solver {
    private static final ZoneId UTC = ZoneOffset.UTC;

    static List<ResultEntry> computeResults(List<Records> calls) {
        Map<Integer, Map<LocalDate, List<Event>>> byCustDay = buildEventLists(calls);

        List<ResultEntry> out = new ArrayList<>();
        for (var cEntry : byCustDay.entrySet()) {
            int customerId = cEntry.getKey();
            for (var dEntry : cEntry.getValue().entrySet()) {
                LocalDate date = dEntry.getKey();
                List<Event> events = dEntry.getValue();

                ResultEntry r = reduceDay(customerId, date, events);
                if (r != null) out.add(r);
            }
        }
        return out;
    }

    private static Map<Integer, Map<LocalDate, List<Event>>> buildEventLists(List<Records> calls) {
        Map<Integer, Map<LocalDate, List<Event>>> map = new HashMap<>();

        for (Records c : calls) {
            // figure out which UTC days this call touches (use end-1ms so midnight falls on the previous day)
            LocalDate first = Instant.ofEpochMilli(c.startTimestamp()).atZone(UTC).toLocalDate();
            LocalDate last  = Instant.ofEpochMilli(c.endTimestamp() - 1).atZone(UTC).toLocalDate();

            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                long dayStart = d.atStartOfDay(UTC).toInstant().toEpochMilli();
                long dayEnd   = d.plusDays(1).atStartOfDay(UTC).toInstant().toEpochMilli();

                // clip to [dayStart, dayEnd) half-open window
                long s = Math.max(c.startTimestamp(), dayStart);
                long e = Math.min(c.endTimestamp(),   dayEnd);

                if (s < e) {
                    List<Event> evs = map
                            .computeIfAbsent(c.customerId(), k -> new HashMap<>())
                            .computeIfAbsent(d,             k -> new ArrayList<>());

                    // add START and END events for that day segment
                    evs.add(new Event(s, true,  c.callId()));  // START
                    evs.add(new Event(e, false, c.callId()));  // END (end-exclusive)
                }
            }
        }
        return map;
    }

    private static ResultEntry reduceDay(int customerId, LocalDate date, List<Event> events) {
        if (events.isEmpty()) return null;

        // Sort by time; for same time: END before START; then by callId for determinism
        events.sort(
                Comparator.comparingLong(Event::time)
                        .thenComparing(e -> e.start() ? 1 : 0)   // END(0) before START(1)
                        .thenComparing(Event::callId)
        );

        Set<String> active = new LinkedHashSet<>(); // stable ordering for snapshots
        int max = 0;
        long bestTs = 0L;
        List<String> bestIds = Collections.emptyList();

        int i = 0;
        while (i < events.size()) {
            long t = events.get(i).time();

            // 1) remove all ENDs at this timestamp
            int j = i;
            while (j < events.size() && events.get(j).time() == t && !events.get(j).start()) {
                active.remove(events.get(j).callId());
                j++;
            }

            // 2) add all STARTs at this timestamp
            int k = j;
            while (k < events.size() && events.get(k).time() == t && events.get(k).start()) {
                active.add(events.get(k).callId());
                k++;
            }

            // 3) update max after processing both groups
            if (active.size() > max) {
                max = active.size();
                bestTs = t;
                bestIds = new ArrayList<>(active); // snapshot
            }

            i = k; // advance to next timestamp block
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
