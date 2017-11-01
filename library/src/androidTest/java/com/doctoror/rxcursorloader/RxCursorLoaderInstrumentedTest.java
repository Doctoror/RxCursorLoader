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
import org.junit.Ignore;
import org.junit.Test;

import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.test.mock.MockContentResolver;

import io.reactivex.observers.TestObserver;

import static org.junit.Assert.*;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public final class RxCursorLoaderInstrumentedTest {

    private static final Uri URI = new Uri.Builder().scheme("content")
            .authority(TestContentProvider.AUTHORITY).build();

    static final String[] QUERY_COLUMNS = new String[]{
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.ARTIST
    };

    private MockContentResolver mContentResolver;

    @Before
    public void setup() {
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(TestContentProvider.AUTHORITY, new TestContentProvider());
    }

    private void assertHasValidCursor(@NonNull final TestObserver<Cursor> observer) {
        observer.assertValueCount(1);
        assertValidCursor(observer.values().get(0));
    }

    private void assertValidCursor(@Nullable final Cursor c) {
        assertNotNull(c);
        assertTrue(c.moveToFirst());
        // Test if can read
        c.getLong(0);
    }

    @Test(expected = IllegalStateException.class)
    public void testNoUriBuilder() throws Exception {
        //noinspection ConstantConditions
        RxCursorLoader.create(mContentResolver, new RxCursorLoader.Query.Builder().create());
    }

    @Test(expected = NullPointerException.class)
    public void testNullContentObserver() throws Exception {
        //noinspection ConstantConditions
        RxCursorLoader.create(null, new RxCursorLoader.Query.Builder()
                .setContentUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI).create());
    }

    @Test(expected = NullPointerException.class)
    public void testNullParams() throws Exception {
        //noinspection ConstantConditions
        RxCursorLoader.create(mContentResolver, null);
    }

    @Test
    public void testQueryParcelable() throws Exception {
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
    @Ignore // FIXME
    public void testCreateSubscribe() throws Exception {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .setProjection(new String[]{MediaStore.Audio.Media._ID})
                .create();

        final TestObserver<Cursor> observer = RxCursorLoader.create(mContentResolver, query).test();
        observer.assertNoErrors();
        observer.assertNotComplete();
        assertHasValidCursor(observer);
        observer.dispose();
    }

    @Test
    public void testSingleSubscribe() throws Exception {
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
