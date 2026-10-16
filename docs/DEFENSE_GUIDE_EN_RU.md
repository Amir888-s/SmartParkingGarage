# Smart Parking Garage defense and live coding guide

## What matters most

Defense/live coding is 50% of the grade; tests are 25%; the working demo is 10%; documentation is 10%; subsystems/facades/persistence are 5%. The implementation helps, but understanding the code and changing it live decide a large part of the result. The teacher may introduce an unannounced failure scenario; the exact task cannot be predicted.

Защита и код на месте дают 50%, тесты — 25%. Поэтому нужно уметь объяснить порядок действий, показать файлы после перезапуска и внести небольшое изменение без нарушения компенсации. Ниже вероятные учебные упражнения, а не гарантированные вопросы преподавателя.

## A simple speech in English

My project is Smart Parking Garage. It uses the Facade pattern to make one parking operation easy for the client. There are three independent subsystems. Subsystem A validates the license plate and saves an admission permit. Subsystem B finds and reserves a parking space. Subsystem C confirms a prepaid payment and saves an overstay deadline.

The client calls ParkingFacade. The facade runs A, then B, then C. Each subsystem has its own entity, business rules and repository. It does not know the other subsystems. I also have AdminFacade for inspection, cancellation and recovery.

All state is saved in CSV files. The workflow has its own saved progress. After a restart, the same workflow ID is used to read the records. A completed stage is not repeated with a new effect.

If B fails because the garage is full, the facade revokes A's permit. If C declines payment, the facade releases B's space first, then revokes A's permit. These are compensating actions because the earlier records were already committed. I can show both cases in the menu.

The tests use separate temporary folders and a fixed clock. They check both rollback depths, restart recovery and persisted final state. JaCoCo measures the line coverage. I use Facade because I need a simple coordinated workflow. Adapter would solve an incompatible interface, which is a different problem.

## The same speech explained in Russian

Мой проект — умная парковка. Клиент вызывает один метод фасада. Подсистема A проверяет номер и сохраняет разрешение. B выбирает и бронирует место. C подтверждает предоплату и сохраняет время окончания парковки. У каждой подсистемы есть собственная сущность, правила и репозиторий. Второй фасад нужен администратору.

Данные и этапы операции сохраняются в CSV. При перезапуске используется тот же ID, поэтому успешный шаг не создаёт второе бронирование или оплату. Если B не находит место, отменяется только A. Если C отклоняет оплату, сначала отменяется B, затем A. Это компенсация уже сохранённых действий, а не обычный rollback одной транзакции.

## Questions and short answers

1. **Where is the Facade? / Где фасад?** ParkingFacade provides park/begin/resume and coordinates A -> B -> C. AdminFacade provides the narrower operational interface.
2. **Why two facades? / Зачем два?** A customer needs parking; an administrator needs inspection, cancellation, controls and recovery. Their purposes differ even though they use the same subsystems.
3. **Where is real sequencing? / Где реальная последовательность?** ParkingFacade.advance commits A before B, and B before C, saving progress between them. compensate performs reverse-order undo.
4. **Are the subsystems independent? / Они независимы?** Gate imports no space or billing classes; space imports no gate or billing classes; billing imports no gate or space classes. SubsystemTest demonstrates all three directly.
5. **How many classes? / Сколько классов?** Thirteen domain subsystem classes: A has four, B four, C five. Facades, workflow, store and application classes are additional coordination/infrastructure classes.
6. **Where are legal transitions enforced? / Где переходы?** In GatePermit, ParkingReservation and Payment themselves. Calling those objects directly still rejects illegal transitions.
7. **Why is revoke idempotent but GatePermit.revoke strict? / Почему разное поведение?** The public service reads persisted state and calls the entity only if the action is needed. The entity protects its invariant. Recovery can retry the service safely.
8. **What is persisted? / Что сохранено?** The three subsystem tables, workflow request/status/events/reason and gate/sensor settings. Payment includes the saved overstay deadline.
9. **What happens after restart? / После перезапуска?** Main keeps pending work available for inspection. The examiner chooses resume or recoverPending; records are reloaded by ID and already-completed effects are reused.
10. **Why save intent before A? / Зачем сначала workflow?** If A commits and the process stops before progress updates, the saved workflow ID still lets recovery find A.
11. **What if B is saved but progress says PENDING_B? / Если журнал отстал?** ReservationManager finds the existing RESERVED record by the same ID and returns it. It does not allocate another space.
12. **What if C paid but progress was not saved? / Если оплата прошла?** A restart resumes with the same payment ID and returns the saved PAID record. If the progress save itself throws while the program is running, compensation checks the real payment and refunds it before B and A.
13. **What is compensation? / Что такое компенсация?** A new action that reverses an already-committed effect. Records remain as REVOKED, RELEASED or REFUNDED for inspection.
14. **Why B before A? / Почему сначала B?** Effects are unwound in reverse dependency order. The downstream reservation must be released before the upstream admission permit is revoked.
15. **Why no refund for DECLINED? / Почему не возвращаем при отказе?** DECLINED never charged money. It remains an audit record. Only PAID can become REFUNDED.
16. **What if compensation fails? / Если отмена сломалась?** The workflow has already saved COMPENSATING. The error is reported; after repairing storage, recovery retries the remaining operations idempotently.
17. **Is this one database transaction? / Это транзакция?** No. Each CSV update is individually atomic. Cross-file consistency uses explicit compensation, resembling a small saga.
18. **Why CSV? / Почему CSV?** The assignment allows files. They simplify setup and make durable state visible. Each gateway still owns its own serialization and data. They are suitable for this local single-writer demo, not a claim of a production distributed system.
19. **How do you prevent two writers? / Два процесса?** GarageApplication holds a file lock for its lifecycle. A second app against the same directory cannot start normally.
20. **What is Least Knowledge? / Минимум знаний?** The client calls park rather than learning three APIs, their ordering and undo rules. README shows own-class before/after examples.
21. **Why Facade instead of Adapter? / Почему не Adapter?** Facade simplifies coordinated use of working APIs. Adapter translates an incompatible API. A hardware driver adapter could exist inside B without replacing the parking facade.
22. **Where are inheritance and composition? / Наследование и композиция?** Exceptions extend RuntimeException. CsvStore implements RowStore. Facades hold subsystem references; services hold repositories and rule objects. That is composition.
23. **How do tests satisfy FIRST? / FIRST?** Small files are fast, @TempDir isolates tests, a fixed clock and deterministic decisions repeat reliably, assertions self-validate, and source/tests are committed together in the supplied Git history.
24. **What is your test DSL? / DSL?** `aGarage(directory).thatIsFull().parks(...).thenOnlyTheGateIsRevoked()` expresses the scenario in parking terms. The assertion methods verify persisted end states and event order.
25. **Why Clock injection? / Clock?** Deadlines can be tested with fixed time and overstay with advanced time. Tests do not wait or depend on the current hour.
26. **What extra failures do you handle? / Дополнительные ошибки?** Invalid plate/duration, gate maintenance before A, duplicate active plate, offline sensors at B and storage errors including interrupted rollback.
27. **Does an alert have its own database? / Где сигнал?** Its deadline is saved in Payment. OverstayAlert calculates whether a PAID record is late. Inspection reads that deadline after restart.
28. **Does compensation move a car? / Машина выезжает назад?** No. A saves a reversible authorization in a simulation. Physical admission and safety controls would require a separate real-world process.
29. **Where do you change the rate? / Где тариф?** PricingPolicy.HOURLY_RATE and its tests. The facade does not calculate the price.
30. **Where do you change capacity? / Где количество мест?** SpaceSensorGrid.CAPACITY and the full-garage simulation. Update both consistently and adjust tests.

## Likely live coding exercises with solutions

These are proposed practice tasks. Implement one at a time, update the matching tests, run `mvn test`, and demonstrate the result in the live app. Rebuild the JAR after changing source, or run Main from IntelliJ. / Это упражнения для подготовки, а не предсказание точного задания. После правки нужен тест и запуск изменённого приложения.

### 1 Change the hourly price

File: `src/main/java/parking/billing/PricingPolicy.java`.

```java
public static final int HOURLY_RATE = 700;
```

Update new-payment assertions from 500/1000/1500/12000 to 700/1400/2100/16800 where applicable. Existing persisted payments keep their original amount; use a fresh workflow and clean/new demo directory for the changed tariff. Do not silently rewrite paid historical records.

RU: Меняется только политика цены и ожидаемые значения тестов. Уже оплаченные записи не пересчитываются автоматически.

### 2 Limit parking to 12 hours

Change the upper bound in `ParkingFacade.begin` and `PricingPolicy.amountFor` from 24 to 12, and update their error text and tests. The first validates early; the second protects direct use of C. A clean refactor could expose a shared rule through C's API, but duplicating a changed bound without updating both locations is a bug.

Add tests:

```java
assertEquals(6000, new PricingPolicy().amountFor(12));
assertThrows(ParkingException.class, () -> new PricingPolicy().amountFor(13));
```

RU: Фасад отсекает плохой запрос до A, а подсистема C защищает собственный API. Поэтому нужно изменить обе проверки.

### 3 Increase the garage to five spaces

Change `SpaceSensorGrid.CAPACITY` to 5. Update the admin simulation so it blocks every space rather than the hard-coded three:

```java
public void simulateFullGarage(boolean full) {
    Set<Integer> blocked = full
        ? java.util.stream.IntStream.rangeClosed(1, SpaceSensorGrid.CAPACITY)
            .boxed().collect(java.util.stream.Collectors.toSet())
        : Set.of();
    spaces.setBlockedSpaces(blocked);
}
```

Update tests that assumed the garage became full after three allocations. Verify option 3 still reaches B failure.

RU: Если увеличить только CAPACITY, старые блокировки {1,2,3} оставят места 4 и 5 свободными и демонстрация ошибки B перестанет работать.

### 4 Add a forbidden plate prefix before A commits

File: `LicensePlateReader.read`, after uppercase conversion and format validation:

```java
if (plate.startsWith("BAD")) {
    throw new ParkingException(INVALID_INPUT, "This plate prefix is blocked");
}
```

Test reader rejection and a facade park request with BAD001. Its workflow must be FAILED with no gate/space/payment records. Reuse INVALID_INPUT for the smallest change, or add BLOCKED_PLATE to Reason if the examiner asks for a separate error category.

RU: Это ошибка A до сохранения разрешения. Компенсация B не нужна, потому что B ещё не запускалась.

### 5 Add a specific exception reason

Add `BLOCKED_PLATE` to `ParkingException.Reason`; throw that reason in the preceding rule. Assert the reason rather than a message substring:

```java
ParkingException error = assertThrows(ParkingException.class,
    () -> new LicensePlateReader().read("BAD001"));
assertEquals(ParkingException.Reason.BLOCKED_PLATE, error.reason());
```

If asked for a separate exception **class**, add `BlockedPlateException extends ParkingException` with a constructor that calls `super(BLOCKED_PLATE, message)`. ParkingFacade already catches ParkingException, so the subclass participates in the existing failure flow.

### 6 Add a 15 minute overstay grace period

File: `OverstayAlert.isOverdue`:

```java
return payment.status() == Payment.Status.PAID
    && now.isAfter(payment.dueAt().plus(java.time.Duration.ofMinutes(15)));
```

Test dueAt + 15 minutes is not overdue and dueAt + 15 minutes + 1 second is overdue. The saved paid deadline remains the same; the grace period changes the alert rule.

RU: Меняется правило уведомления, а не сумма оплаты и не состояние бронирования.

### 7 Force an offline-sensor failure after A

This behavior already exists and can be demonstrated without source edits:

```java
app.admin().setSensorsOnline(false);
Workflow result = app.parking().park("SENSOR01", 1, Card.APPROVE);
assertEquals(Workflow.Status.COMPENSATED, result.status());
```

In the CLI, option 11 false, then option 1. Show A committed -> A revoked and no B record. Restore sensors with option 11 true. The availability flag is persisted, so restarting alone does not make the sensors healthy.

RU: Ошибка B запускает тот же алгоритм отмены A, независимо от того, вызвана она отсутствием мест или отказом датчика.

### 8 Add a new payment failure without breaking compensation

For example, reject payments above 5000 KZT. Put the decision in `PaymentTerminal.confirm` after validating NEW and before pay. Save DECLINED, then throw PAYMENT_DECLINED:

```java
if (amount > 5000) {
    payment.decline();
    repository.save(payment);
    throw new ParkingException(PAYMENT_DECLINED, "Terminal amount limit exceeded");
}
```

A request for 11 hours at 500 KZT/hour reaches C, which declines. Assert B RELEASED, A REVOKED and their event order. A request for 10 hours should still complete. The old 24-hour acceptance tests need to be adjusted to distinguish price validity from terminal approval rules.

RU: Новое ограничение оплаты находится в C. Фасад уже умеет отменять B и A при такой ошибке; новый if в фасаде не нужен.

### 9 Write a restart test

Add a test in WorkflowTest, which already has its own TempDir and the open helper:

```java
@Test void restartKeepsTheReservationAndFinishesPayment() {
    String id;
    try (GarageApplication first = open()) {
        id = first.parking().begin("RELOAD01", 2, APPROVE, AFTER_B).id();
    }
    try (GarageApplication second = open()) {
        assertEquals(COMPLETED, second.parking().resume(id).status());
        assertEquals("PAID", second.admin().inspect(id).payment());
        assertEquals(1000, second.admin().inspect(id).amount());
    }
}
```

RU: Нужно закрыть первый экземпляр, чтобы освободить блокировку. Второй читает ту же папку; новая временная папка внутри второй части сделала бы тест бессмысленным.

### 10 Write a test that checks rollback order

Use the existing test DSL or inspect the saved events:

```java
Workflow result = app.parking().park("DECLINE1", 1, Card.DECLINE);
List<String> events = app.admin().inspect(result.id()).events();
assertEquals(List.of("A committed", "B committed", "B released", "A revoked"), events);
```

Also assert reloaded GateRepository and SpaceRepository statuses. An event-string test alone would not prove the real stored resources were reversed.

### 11 Add a business rule to prevent parking at night

Decide the owning subsystem before coding. An admission rule belongs to A, so inject a Clock into GateController (update GarageApplication and every direct-test constructor) and check the configured garage timezone before creating a new permit. Do not apply it when returning a matching existing permit, or a restart during closed hours could incorrectly reject an already-committed A.

RU: Главное — сохранить идемпотентность. Новое правило относится к новой операции, а не должно ломать повторное чтение уже выданного разрешения. Если нужна постоянная настройка часов работы, её должен сохранять GateRepository.

### 12 Handle interrupted compensation

The implementation already persists COMPENSATING before undo and retries safely. Show `WorkflowTest.storageFailureDuringRollbackKeepsDurableRetryState`. Explain the order: keep COMPENSATING -> repair storage -> reload entities -> release B if still RESERVED -> revoke A if still AUTHORIZED -> save COMPENSATED.

Do not catch the undo failure and force COMPENSATED. Do not delete all records to hide inconsistency. / Нельзя объявлять отмену завершённой, пока сохранённые ресурсы реально не отменены.

## A reliable order for live coding

1. Say which rule the teacher is changing and which subsystem owns it.
2. Find the class using the class guide; explain the relevant method before editing.
3. Make the smallest correct edit. Update constructors/serialization if the data shape changes.
4. Write or update one meaningful success/boundary/failure test. If the new failure occurs at B or C, assert the required compensation depth and persisted state.
5. Run `mvn test`; if it fails, read the failing assertion and explain the mismatch.
6. Run the changed Main in IntelliJ, or rebuild the JAR with build.cmd/build.sh, then show the actual menu behavior.

## What to study first

First learn ParkingFacade.advance and compensate, then the three entity transitions and idempotent service methods. Next learn the file repositories and restart tests. Finally rehearse the demo and the small pricing, validation, capacity and alert edits.

Сначала разбери последовательность и компенсацию, затем состояния сущностей и повторный вызов по тому же ID. После этого — файлы и тесты перезапуска. Обязательно потренируй изменение цены, количества мест и новое условие ошибки.
