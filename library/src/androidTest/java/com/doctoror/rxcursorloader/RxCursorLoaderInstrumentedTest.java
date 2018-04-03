/*
 * Copyright (C) 2016 Yaroslav Mytkalyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.doctoror.rxcursorloader;

import org.junit.Before;
import org.junit.Test;

import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.test.mock.MockContentResolver;

import io.reactivex.BackpressureStrategy;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

import static org.junit.Assert.*;

public final class RxCursorLoaderInstrumentedTest {

    private static final Uri URI = new Uri.Builder().scheme("content")
            .authority(TestContentProvider.AUTHORITY).build();

    private final MockContentResolver mContentResolver = new MockContentResolver();

    @Before
    public void setup() {
        mContentResolver.addProvider(TestContentProvider.AUTHORITY, new TestContentProvider());
    }

    private void assertHasValidCursor(@NonNull final BaseTestConsumer observer) {
        observer.assertValueCount(1);
        assertValidCursor((Cursor) observer.values().get(0));
    }

    private void assertValidCursor(@Nullable final Cursor c) {
        assertNotNull(c);
        assertTrue(c.moveToFirst());
        // Test if can read
        c.getLong(0);
    }

    @Test(expected = IllegalStateException.class)
    public void testNoUriBuilder() {
        //noinspection ConstantConditions
        RxCursorLoader.flowable(
                mContentResolver,
                new RxCursorLoader.Query.Builder().create(),
                Schedulers.trampoline(),
                BackpressureStrategy.ERROR);
    }

    @Test(expected = NullPointerException.class)
    public void testNullContentResolver() {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI).create();
        //noinspection ConstantConditions
        RxCursorLoader.flowable(
                null, query, Schedulers.trampoline(), BackpressureStrategy.ERROR);
    }

    @Test(expected = NullPointerException.class)
    public void testNullQuery() {
        //noinspection ConstantConditions
        RxCursorLoader.flowable(
                mContentResolver, null, Schedulers.trampoline(), BackpressureStrategy.ERROR);
    }

    @Test
    public void testQueryParcelable() {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .setProjection(new String[]{MediaStore.Audio.Media._ID})
                .setSortOrder(MediaStore.Audio.Artists.ARTIST)
                .setSelection(MediaStore.Audio.Artists.ARTIST + "=?")
                .setSelectionArgs(new String[]{"Oh Long Johnson"})
                .create();

        final Parcel parcel = Parcel.obtain();
        query.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        final RxCursorLoader.Query fromParcel = RxCursorLoader.Query.CREATOR
                .createFromParcel(parcel);
        assertEquals(query, fromParcel);
    }

    @Test
    public void testFlowableSubscribe() {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .setProjection(new String[]{MediaStore.Audio.Media._ID})
                .create();

        final TestSubscriber<Cursor> observer = RxCursorLoader.flowable(
                mContentResolver,
                query,
                Schedulers.trampoline(),
                BackpressureStrategy.ERROR).test();

        observer.assertNoErrors();
        observer.assertNotComplete();
        assertHasValidCursor(observer);

        observer.dispose();
    }

    @Test
    public void testSingleSubscribe() {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .setProjection(new String[]{MediaStore.Audio.Media._ID})
                .create();

        final TestObserver<Cursor> observer = RxCursorLoader.single(mContentResolver, query).test();
        observer.assertNoErrors();
        observer.assertComplete();
        assertHasValidCursor(observer);

        observer.dispose();
    }
}
