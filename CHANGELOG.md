# 2.1.1
 - Added `observable` factory method to be able to use Observables again;
 - Removed `HandlerThread` usage in favor of main-threaded `ContentObserver` `Handler`;
 - Decreased `synchronize` scope.

# 2.1.0
 - Fixed single not setting `QueryReturnedNullException` when provider returns null;
 - Added `flowable` method which also accepts `Scheduler` and `BackpressureStrategy`;
 - `create` method is deprecated in favor of `flowable`.

# 2.0.2

- Downgrade to Java 7 ([<s>issue #3</s>](/../../issues/3))
