##Description

An RX replacement for [android.content.CursorLoader](https://developer.android.com/reference/android/content/CursorLoader.html)

Min API level 9

##Setup

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.doctoror.rxcursorloader/library/badge.png?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.doctoror.rxcursorloader/library)

```groovy
compile 'com.github.doctoror.rxcursorloader:library:[version]'
```

## Usage

Create a Query using Query.Builder. The required parameter is only a content URI.
```java
final CursorLoaderObservable.Query query = new CursorLoaderObservable.Query.Builder()
        .setContentUri(MediaStore.Audio.Media.INTERNAL_CONTENT_URI)
        .setProjection(new String[]{MediaStore.Audio.Media._ID})
        .setSortOrder(MediaStore.Audio.Artists.ARTIST)
        .setSelection(MediaStore.Audio.Artists.ARTIST + "=?")
        .setSelectionArgs(new String[] {"Oh Long Johnson"})
        .create();
```

Use create(ContentResolver, Query) to create new RxCursorLoader instance.
Do not lose Subscription, you will need it to unsubscribe.

```java
mCursorSubscription = CursorLoaderObservable.create(getContentResolver(), params)
                .subscribe(cursor -> mAdapter.swapCursor(cursor));
```

When the Cursor is loaded, it is passed to onNext(Object) (can be null).
Every time the content changes, the Cursor will be reloaded and passed to onNext(Object).
Observer#onCompleted() and Observer#onError(Throwable) are never called.

You must call Subscriber#unsubscribe() when finished. Do not use com.trello.rxlifecycle as
it does not call unsubscribe. Cursor is automatically closed on unsubscribe so make sure
nothing is using Cursor or else you may get a RuntimeException

```java
@Override
protected void onStop() {
    super.onStop();
    // stop using Cursor
    mAdapter.swapCursor(null);
    
    // Unsubscribe to close the Cursor and stop monitoring for ContentObserver changes
    mCursorSubscription.unsubscribe();
}
```

If you need to load once, use asObservable().take(1), this way you don't need to unsubscribe

```java
RxCursorLoader.create(resolver, query).asObservable().take(1).subscribe(this::handleCursor);
```

##License

```
Copyright 2016 Yaroslav Mytkalyk

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

```
