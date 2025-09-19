The flows that we need to design in this are 
1. Locker selection flow (this should be thread safe)
2. Delivery flow
3. pickup flow

requirement </br>

Get by locker zip code and name 


🔹 Entities and Their Roles

Item

Represents a single product in an order (id + quantity).

Used inside Order.

Order

Holds the list of items, delivery location, and customerId.

Associated with a Package (and later a LockerPackage).

Package

Represents the physical packaging of an order.

Can be “packed” before delivery.

Extended by LockerPackage.

LockerPackage (extends Package)

Adds locker-specific fields (OTP code, valid days, delivery personId, delivery time).

Used in LockerTask operations.

LockerSize (enum)

Defines possible sizes of lockers (SMALL, MEDIUM, LARGE, etc.).

Used when assigning lockers (LockerService.requestLocker).

LockerState (enum)

Tracks state of a locker (AVAILABLE, BOOKED, CLOSED).

Locker

A physical locker box.

Can addPackage or removePackage if correct OTP is provided.

LockerLocation

Represents a set of lockers at one physical location.

Has metadata (longitude, latitude, open/close time).

Notification

Used to inform the customer about pickup or return (with lockerId + OTP).

Customer

Places orders, requests returns, and receives notifications.

DeliveryPerson

Executes LockerTasks assigned by the system (stateless execution).

LockerTask (abstract + subclasses)

DeliverToLockerTask → puts package in locker.

PickupFromLockerTask → retrieves package using OTP.

ReturnToLockerTask → customer returns package into locker.

LockerService (Singleton)

Central orchestrator.

Finds/assigns lockers, verifies OTP, approves returns.

Uses TaskAssignmentStrategy to decide locker assignment.

TaskAssignmentStrategy

Pluggable strategy interface.

Example implemented: NearestLockerStrategy.

Driver (Main)

Demo runner for 3 scenarios: delivery, pickup, return.