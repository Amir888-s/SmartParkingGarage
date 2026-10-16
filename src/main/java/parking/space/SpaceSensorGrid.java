package parking.space;

import java.util.Set;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.*;

/** Pure decision logic based on independently supplied sensor and booking data. */
public final class SpaceSensorGrid {
    public static final int CAPACITY = 3;
    public int firstFree(Set<Integer> occupied, Set<Integer> blocked, boolean online) {
        if (!online) throw new ParkingException(SENSOR_OFFLINE, "Space sensors are offline");
        for (int space = 1; space <= CAPACITY; space++) {
            if (!occupied.contains(space) && !blocked.contains(space)) return space;
        }
        throw new ParkingException(NO_SPACE, "No space available: sensor grid reports a full garage");
    }
}
