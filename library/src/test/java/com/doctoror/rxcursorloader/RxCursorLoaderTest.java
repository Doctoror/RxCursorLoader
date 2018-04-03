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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.reactivex.BackpressureStrategy;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public final class RxCursorLoaderTest {

    private static final Uri URI = new Uri.Builder().scheme("content")
            .authority("com.doctoror.rxcursorloader.test.provider").build();

    private final ContentResolver contentResolver = mock(ContentResolver.class);

    @Before
    public void setup() {
        final Cursor stubCursor = mock(Cursor.class);
        when(contentResolver
                .query(eq(URI), (String[]) any(), (String) any(), (String[]) any(), (String) any()))
                .thenReturn(stubCursor);
    }

    private void assertHasValidOpenCursor(@NonNull final BaseTestConsumer observer) {
        observer.assertValueCount(1);
        assertValidOpenCursor((Cursor) observer.values().get(0));
    }

    private void assertValidOpenCursor(@Nullable final Cursor c) {
        assertNotNull(c);
        verify(c, never()).close();
    }

    private void givenQueryReturnsNull() {
        when(contentResolver
                .query(eq(URI), (String[]) any(), (String) any(), (String[]) any(), (String) any()))
                .thenReturn(null);
    }

    @NonNull
    private RxCursorLoader.Query buildQuery() {
        return new RxCursorLoader.Query.Builder()
                .setContentUri(URI).create();
    }

    @Test(expected = IllegalStateException.class)
    public void noUriThrowsIllegalStateException() {
        //noinspection ConstantConditions
        RxCursorLoader.flowable(
                contentResolver,
                new RxCursorLoader.Query.Builder().create(),
                Schedulers.trampoline(),
                BackpressureStrategy.ERROR);
    }

    @Test(expected = NullPointerException.class)
    public void nullContentResolverThrowsNullPointerException() {
        //noinspection ConstantConditions
        RxCursorLoader.flowable(
                null, buildQuery(), Schedulers.trampoline(), BackpressureStrategy.ERROR);
    }

    @Test(expected = NullPointerException.class)
    public void nullQueryThrowsNullPointerException() {
        //noinspection ConstantConditions
        RxCursorLoader.flowable(
                contentResolver, null, Schedulers.trampoline(), BackpressureStrategy.ERROR);
    }

    @Test
    public void queryIsValidParcelable() {
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
    public void flowableReturnsCursorFromContentProvider() {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .create();

        final TestSubscriber<Cursor> observer = RxCursorLoader.flowable(
                contentResolver,
                query,
                Schedulers.trampoline(),
                BackpressureStrategy.ERROR).test();

        observer.assertNoErrors();
        observer.assertNotComplete();
        assertHasValidOpenCursor(observer);

        observer.dispose();
    }

    @Test
    public void flowableErrorWhenProviderReturnsNull() {
        givenQueryReturnsNull();

        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .create();

        final TestSubscriber<Cursor> observer = RxCursorLoader.flowable(
                contentResolver,
                query,
                Schedulers.trampoline(),
                BackpressureStrategy.ERROR).test();

        observer.assertError(QueryReturnedNullException.class);

        observer.dispose();
    }

    @Test
    public void singleReturnsCursorFromContentProvider() {
        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .create();

        final TestObserver<Cursor> observer = RxCursorLoader.single(contentResolver, query).test();
        observer.assertNoErrors();
        observer.assertComplete();
        assertHasValidOpenCursor(observer);

        observer.dispose();
    }

    @Test
    public void singleErrorWhenProviderReturnsNull() {
        givenQueryReturnsNull();

        final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
                .setContentUri(URI)
                .create();

        final TestObserver<Cursor> observer = RxCursorLoader.single(contentResolver, query).test();
        observer.assertError(QueryReturnedNullException.class);

        observer.dispose();
    }
}
