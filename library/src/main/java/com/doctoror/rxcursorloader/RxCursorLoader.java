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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Arrays;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

/**
 * An RX replacement for {@link android.content.CursorLoader}
 * <br>
 * <br>
 * Usage:
 * <br>
 * Create a {@link Query} using {@link Query.Builder}. The required parameter is only a content
 * URI.
 * <br><blockquote><pre>
 * final RxCursorLoader.Query query = new RxCursorLoader.Query.Builder()
 *     .setContentUri(MediaStore.Audio.Media.INTERNAL_CONTENT_URI)
 *     .setProjection(new String[]{MediaStore.Audio.Media._ID})
 *     .setSortOrder(MediaStore.Audio.Artists.ARTIST)
 *     .setSelection(MediaStore.Audio.Artists.ARTIST + "=?")
 *     .setSelectionArgs(new String[] {"Oh Long Johnson"})
 *     .create();
 * }
 * </pre></blockquote>
 *
 * If you need to load only once, use {@link #single(ContentResolver, Query)}.
 * <br><br>
 * If you need the loader to register ContentObserver and reload cursor passing it to onNext()
 * every time content changes, like {@link android.content.CursorLoader}, use
 * {@link #create(ContentResolver, Query)}.
 */
public final class RxCursorLoader {

    private static final String TAG = "RxCursorLoader";

    /**
     * Set this to true to enable logging
     */
    public static boolean LOG = false;

    private RxCursorLoader() {
        throw new UnsupportedOperationException();
    }

    /**
     * Create a new {@link Observable} that emits items from a {@link ContentResolver} query.
     * This acts like {@link android.content.CursorLoader}.<br>
     * When a non-null Cursor is loaded, it is passed to {@link Observer#onNext(Object)}}. If the
     * query returns null, {@link QueryReturnedNullException} is passed to {@link
     * Observer#onError(Throwable)}
     * <br>
     * Every time the content changes, the Cursor will be reloaded and passed to {@link
     * Observer#onNext(Object)}. Make sure to close old cursor because Cursors are not automatically
     * closed.
     * <br>
     * {@link Observer#onError(Throwable)}} is called if {@link RuntimeException} is caught when
     * running a query
     * <br>
     * <br><b>You must call {@link Disposable#dispose()} when finished.</b>
     *
     * <br><blockquote><pre>
     * protected void onStop() {
     *     super.onStop();
     *     // stop using Cursor and close it
     *     mAdapter.changeCursor(null);
     *     // Unsubscribe to stop monitoring for ContentObserver changes
     *     mCursorDisposable.dispose();
     * }
     * </pre></blockquote>
     *
     * @param resolver {@link ContentResolver} to use
     * @param query    the {@link Query} to use
     * @return new {@link Observable}.
     */
    @NonNull
    public static Observable<Cursor> create(@NonNull final ContentResolver resolver,
            @NonNull final Query query) {
        //noinspection ConstantConditions
        if (resolver == null) {
            throw new NullPointerException("ContentResolver param must not be null");
        }
        //noinspection ConstantConditions
        if (query == null) {
            throw new NullPointerException("Params param must not be null");
        }
        final CursorLoaderOnSubscribe onSubscribe = new CursorLoaderOnSubscribe(resolver, query);
        return Observable.create(onSubscribe)
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        onSubscribe.release();
                    }
                });
    }

    /**
     * Create a new {@link Single} that loads {@link Cursor} once and does not close it.
     * Calls {@link Consumer#accept(Object)} once non-null {@link Cursor} is loaded.
     * If the query returns null, {@link QueryReturnedNullException} is thrown.
     *
     * @param resolver {@link ContentResolver} to use
     * @param query    the {@link Query} to use
     * @return new {@link Single}.
     */
    @NonNull
    public static Single<Cursor> single(@NonNull final ContentResolver resolver,
            @NonNull final Query query) {
        //noinspection ConstantConditions
        if (resolver == null) {
            throw new NullPointerException("ContentResolver param must not be null");
        }
        //noinspection ConstantConditions
        if (query == null) {
            throw new NullPointerException("Params param must not be null");
        }

        return Single.create(new CursorLoaderOnSubscribeSingle(resolver, query));
    }

    private static final class CursorLoaderOnSubscribe
            implements ObservableOnSubscribe<Cursor> {

        private final Object mLock = new Object();

        @NonNull
        private final ContentResolver mContentResolver;

        @NonNull
        private final Query mQuery;

        private Handler mHandler;

        private ObservableEmitter<Cursor> mEmitter;

        private ContentObserver mResolverObserver;

        CursorLoaderOnSubscribe(@NonNull final ContentResolver resolver,
                @NonNull final Query query) {
            mContentResolver = resolver;
            mQuery = query;
        }

        @Override
        public void subscribe(final ObservableEmitter<Cursor> emitter) throws Exception {
            final HandlerThread handlerThread = new HandlerThread(TAG.concat(".HandlerThread"));
            handlerThread.start();
            synchronized (mLock) {
                mHandler = new Handler(handlerThread.getLooper());
                mEmitter = emitter;
                mContentResolver.registerContentObserver(mQuery.contentUri, true,
                        getResolverObserver());
            }
            reload();
        }

        private void release() {
            synchronized (mLock) {
                if (mResolverObserver != null) {
                    mContentResolver.unregisterContentObserver(mResolverObserver);
                    mResolverObserver = null;
                }
                mEmitter = null;
                mHandler.getLooper().quit();
            }
        }

        /**
         * Loads new {@link Cursor}.
         *
         * This must be called from {@link #subscribe(ObservableEmitter)} thread
         */
        private synchronized void reload() {
            synchronized (mLock) {
                if (LOG) {
                    Log.d(TAG, mQuery.toString());
                }

                final Cursor c = mContentResolver.query(
                        mQuery.contentUri,
                        mQuery.projection,
                        mQuery.selection,
                        mQuery.selectionArgs,
                        mQuery.sortOrder);

                if (mEmitter != null && !mEmitter.isDisposed()) {
                    if (c != null) {
                        mEmitter.onNext(c);
                    } else {
                        mEmitter.onError(new QueryReturnedNullException());
                    }
                }
            }
        }

        /**
         * Creates the {@link ContentObserver} to observe {@link Cursor} changes.
         * It must be initialized from thread in which {@link #subscribe(ObservableEmitter)} is
         * called.
         *
         * @return the {@link ContentObserver} to observe {@link Cursor} changes.
         */
        @NonNull
        private ContentObserver getResolverObserver() {
            if (mResolverObserver == null) {
                mResolverObserver = new ContentObserver(mHandler) {

                    @Override
                    public void onChange(final boolean selfChange) {
                        super.onChange(selfChange);
                        reload();
                    }
                };
            }
            return mResolverObserver;
        }
    }

    private static final class CursorLoaderOnSubscribeSingle
            implements SingleOnSubscribe<Cursor> {

        @NonNull
        private final ContentResolver mContentResolver;

        @NonNull
        private final Query mQuery;

        CursorLoaderOnSubscribeSingle(@NonNull final ContentResolver resolver,
                @NonNull final Query query) {
            mContentResolver = resolver;
            mQuery = query;
        }

        @Override
        public void subscribe(final SingleEmitter<Cursor> emitter) throws Exception {
            if (LOG) {
                Log.d(TAG, mQuery.toString());
            }

            final Cursor c = mContentResolver.query(
                    mQuery.contentUri,
                    mQuery.projection,
                    mQuery.selection,
                    mQuery.selectionArgs,
                    mQuery.sortOrder);

            emitter.onSuccess(c);
        }
    }

    /**
     * Parameters for {@link RxCursorLoader}
     */
    public static final class Query implements Parcelable {

        Uri contentUri;
        String[] projection;
        String selection;
        String[] selectionArgs;
        String sortOrder;

        Query() {

        }

        Query(@NonNull final Parcel p) {
            contentUri = p.readParcelable(Uri.class.getClassLoader());
            projection = p.createStringArray();
            selection = p.readString();
            selectionArgs = p.createStringArray();
            sortOrder = p.readString();
        }

        @Override
        public void writeToParcel(final Parcel p, final int i) {
            p.writeParcelable(contentUri, 0);
            p.writeStringArray(projection);
            p.writeString(selection);
            p.writeStringArray(selectionArgs);
            p.writeString(sortOrder);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        // Generated by Android Studio
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Query query = (Query) o;

            if (contentUri != null ? !contentUri.equals(query.contentUri)
                    : query.contentUri != null) {
                return false;
            }
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            if (!Arrays.equals(projection, query.projection)) {
                return false;
            }
            if (selection != null ? !selection.equals(query.selection) : query.selection != null) {
                return false;
            }
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            //noinspection SimplifiableIfStatement
            if (!Arrays.equals(selectionArgs, query.selectionArgs)) {
                return false;
            }
            return sortOrder != null ? sortOrder.equals(query.sortOrder) : query.sortOrder == null;

        }

        // Generated by Android Studio
        @Override
        public int hashCode() {
            int result = contentUri != null ? contentUri.hashCode() : 0;
            result = 31 * result + Arrays.hashCode(projection);
            result = 31 * result + (selection != null ? selection.hashCode() : 0);
            result = 31 * result + Arrays.hashCode(selectionArgs);
            result = 31 * result + (sortOrder != null ? sortOrder.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Params{" +
                    "mContentUri=" + contentUri +
                    ", mProjection=" + Arrays.toString(projection) +
                    ", mSelection='" + selection + '\'' +
                    ", mSelectionArgs=" + Arrays.toString(selectionArgs) +
                    ", mSortOrder='" + sortOrder + '\'' +
                    '}';
        }

        public static final Parcelable.Creator<Query> CREATOR = new Creator<Query>() {

            @Override
            public Query createFromParcel(final Parcel parcel) {
                return new Query(parcel);
            }

            @Override
            public Query[] newArray(final int size) {
                return new Query[size];
            }
        };

        /**
         * {@link Query} builder.
         * <p>
         * The only required parameter is a content URI.
         */
        public static final class Builder {

            private Uri mContentUri;
            private String[] mProjection;
            private String mSelection;
            private String[] mSelectionArgs;
            private String mSortOrder;

            public Builder() {

            }

            @NonNull
            public Builder setContentUri(@NonNull final Uri contentUri) {
                mContentUri = contentUri;
                return this;
            }

            @NonNull
            public Builder setProjection(final String[] projection) {
                mProjection = projection;
                return this;
            }

            @NonNull
            public Builder setSelection(final String selection) {
                mSelection = selection;
                return this;
            }

            @NonNull
            public Builder setSelectionArgs(final String[] selectionArgs) {
                mSelectionArgs = selectionArgs;
                return this;
            }

            @NonNull
            public Builder setSortOrder(final String sortOrder) {
                mSortOrder = sortOrder;
                return this;
            }

            /**
             * Creates the {@link Query}
             *
             * @return the {@link Query}
             * @throws IllegalStateException if content uri is null
             */
            @NonNull
            public Query create() {
                if (mContentUri == null) {
                    throw new IllegalStateException("Content URI not set");
                }
                final Query query = new Query();
                query.contentUri = mContentUri;
                query.projection = mProjection;
                query.selection = mSelection;
                query.selectionArgs = mSelectionArgs;
                query.sortOrder = mSortOrder;
                return query;
            }
        }
    }
}
