package parking.billing;

import java.time.*;
import java.util.*;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.*;

public final class PaymentTerminal {
    public enum Card { APPROVE, DECLINE }
    private final BillingRepository repository;
    private final PricingPolicy pricing;
    private final OverstayAlert alert;
    private final Clock clock;
    public PaymentTerminal(BillingRepository repository, PricingPolicy pricing, OverstayAlert alert, Clock clock) {
        this.repository = repository; this.pricing = pricing; this.alert = alert; this.clock = clock;
    }
    public Payment confirm(String id, int hours, Card card) {
        int amount = pricing.amountFor(hours);
        Payment payment = repository.find(id).orElseGet(() -> new Payment(id, amount, alert.dueAt(clock.instant(), hours), Payment.Status.NEW));
        if (payment.amount() != amount) throw new ParkingException(CONFLICT, "Existing payment has a different amount");
        if (payment.status() == Payment.Status.PAID) return payment;
        if (payment.status() == Payment.Status.DECLINED) throw new ParkingException(PAYMENT_DECLINED, "Payment was declined");
        if (payment.status() != Payment.Status.NEW) throw new ParkingException(CONFLICT, "Payment has already been cancelled or refunded");
        if (card == null) throw new ParkingException(INVALID_INPUT, "Payment decision is required");
        if (card == Card.DECLINE) {
            payment.decline(); repository.save(payment);
            throw new ParkingException(PAYMENT_DECLINED, "Payment terminal declined the card");
        }
        payment.pay(); repository.save(payment); return payment;
    }
    public void reverse(String id) {
        repository.find(id).ifPresent(payment -> {
            if (payment.status() == Payment.Status.PAID) { payment.refund(); repository.save(payment); }
            else if (payment.status() == Payment.Status.NEW) { payment.cancel(); repository.save(payment); }
        });
    }
    public Optional<Payment> find(String id) { return repository.find(id); }
    public List<Payment> all() { return repository.all(); }
    public List<String> overdueIds() { return repository.all().stream().filter(p -> alert.isOverdue(p, clock.instant())).map(Payment::id).toList(); }
}
