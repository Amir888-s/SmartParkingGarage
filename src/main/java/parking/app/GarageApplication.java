package parking.app;

import java.nio.channels.*;
import java.nio.file.*;
import java.time.Clock;
import java.io.IOException;
import parking.store.*;
import parking.gate.*;
import parking.space.*;
import parking.billing.*;
import parking.facade.*;

/** Composition root and single-writer lock; closes the lock on real restarts. */
public final class GarageApplication implements AutoCloseable {
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ParkingFacade parking;
    private final AdminFacade admin;
    public GarageApplication(Path directory, Clock clock) {
        CsvStore store = new CsvStore(directory);
        try {
            lockChannel = FileChannel.open(directory.resolve("garage.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquired;
            try { acquired = lockChannel.tryLock(); }
            catch (OverlappingFileLockException e) { acquired = null; }
            if (acquired == null) { lockChannel.close(); throw new IOException("Another garage process already owns this store"); }
            lock = acquired;
        } catch (IOException e) { throw new StorageException("Cannot lock garage store", e); }
        GateController gate = new GateController(new GateRepository(store), new LicensePlateReader());
        ReservationManager spaces = new ReservationManager(new SpaceRepository(store), new SpaceSensorGrid());
        PaymentTerminal billing = new PaymentTerminal(new BillingRepository(store), new PricingPolicy(), new OverstayAlert(), clock);
        WorkflowRepository workflows = new WorkflowRepository(store);
        parking = new ParkingFacade(gate, spaces, billing, workflows);
        admin = new AdminFacade(gate, spaces, billing, workflows, parking);
    }
    public ParkingFacade parking() { return parking; }
    public AdminFacade admin() { return admin; }
    @Override public void close() {
        try { lock.release(); lockChannel.close(); }
        catch (IOException e) { throw new StorageException("Cannot close garage store", e); }
    }
}
