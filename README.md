## Description

An RX replacement for [android.content.CursorLoader](https://developer.android.com/reference/android/content/CursorLoader.html)

Min API level 9

## Setup

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.doctoror.rxcursorloader/library/badge.png?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.doctoror.rxcursorloader/library)

For RxJava 2 
```groovy
compile 'com.github.doctoror.rxcursorloader:library:2.0.1'
```

If you need RxJava 1, you can use the old version
```groovy
compile 'com.github.doctoror.rxcursorloader:library:1.1.5'
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

Thare are two cases covered by this library.

1) You want to load the Cursor once

```java
RxCursorLoader.single(getContentResolver(), query)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(this::handleAndCloseCursor);
```
2) You want to reload a Cursor every time the content under URI changes, just like CursorLoader does.
Note that unlike CursorLoader, this does not close the Cursor for you, so make sure to close old cursor once onNext() is called.

```java
mCursorDisposable = CursorLoaderObservable.create(getContentResolver(), params)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(c -> mCursorAdapter.changeCursor(c));
```

You must call Disposable.dispose() when finished so that the library unregisters the ContentObserver

```java
@Override
protected void onStop() {
    super.onStop();
    // stop using Cursor
    mAdapter.changeCursor(null);
    
    // Unsubscribe to close the Cursor and stop monitoring for ContentObserver changes
    mCursorDisposable.dispose();
}
```

If ContentResolver query returns null, `onError()` will be called with `QueryReturnedNullException`

## License

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
