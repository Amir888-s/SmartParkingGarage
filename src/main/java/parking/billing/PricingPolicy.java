package parking.billing;

import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.INVALID_INPUT;

public final class PricingPolicy {
    public static final int HOURLY_RATE = 500;
    public int amountFor(int hours) {
        if (hours < 1 || hours > 24) throw new ParkingException(INVALID_INPUT, "Parking duration must be 1-24 whole hours");
        return hours * HOURLY_RATE;
    }
}
