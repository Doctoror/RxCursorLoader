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

import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * An RX replacement for {@link android.content.CursorLoader}
 * <br>
 * <br>
 * Usage:
 * <br>
 * Create a {@link Query} using {@link Query.Builder}. The required parameter is only a content
 * URI.
 * <br><blockquote><pre>
 * final CursorLoaderObservable.Query query = new CursorLoaderObservable.Query.Builder()
 *     .setContentUri(MediaStore.Audio.Media.INTERNAL_CONTENT_URI)
 *     .setProjection(new String[]{MediaStore.Audio.Media._ID})
 *     .setSortOrder(MediaStore.Audio.Artists.ARTIST)
 *     .setSelection(MediaStore.Audio.Artists.ARTIST + "=?")
 *     .setSelectionArgs(new String[] {"Oh Long Johnson"})
 *     .create();
 * }
 * </pre></blockquote>
 *
 * Use {@link #create(ContentResolver, Query)} to create new {@link RxCursorLoader} instance.
 * Do not lose {@link Subscription}, you will need it to unsubscribe.
 * <br><blockquote><pre>
 * mCursorSubscription = CursorLoaderObservable.create(getContentResolver(), query)
 *     .subscribe(cursor -&gt; mAdapter.swapCursor(cursor));
 * </pre></blockquote>
 *
 * When the Cursor is loaded, it is passed to {@link Observer#onNext(Object)}} (can be null).
 * Every time the content changes, the Cursor will be reloaded and passed to {@link
 * Observer#onNext(Object)}}. {@link Observer#onCompleted()}} and {@link
 * Observer#onError(Throwable)}} are never called.
 * <br>
 * <br><b>You must call {@link Subscriber#unsubscribe()} when finished.</b> Do not use
 * com.trello.rxlifecycle as it does not call unsubscribe. Cursor is automatically closed on
 * unsubscribe so make sure nothing is using Cursor or else you may get a RuntimeException
 *
 * <br><blockquote><pre>
 * protected void onStop() {
 *     super.onStop();
 *     // stop using Cursor
 *     mAdapter.swapCursor(null);
 *     // Unsubscribe to close the Cursor and stop monitoring for ContentObserver changes
 *     mCursorSubscription.unsubscribe();
 * }
 * </pre></blockquote>
 *
 * You may call {@link #reloadWithNewQuery(Query)} if your query changes (for example, if user
 * changes search filter).
 */
public final class RxCursorLoader {

    private static final String TAG = "RxCursorLoader";

    /**
     * Set this to true to enable logging
     */
    public static boolean LOG = false;

    private Observable<Cursor> mObservable;
    private final CursorLoaderOnSubscribe mOnSubscribe;

    private RxCursorLoader(@NonNull final Observable<Cursor> observable,
            @NonNull final CursorLoaderOnSubscribe onSubscribe) {
        mObservable = observable;
        mOnSubscribe = onSubscribe;
    }

    @NonNull
    public final Subscription subscribe(final Observer<? super Cursor> observer) {
        return mObservable.subscribe(observer);
    }

    @NonNull
    public final Subscription subscribe(final Action1<? super Cursor> action1) {
        return mObservable.subscribe(action1);
    }

    @NonNull
    public final Subscription subscribe(final Action1<? super Cursor> onNext,
            final Action1<Throwable> onError) {
        return mObservable.subscribe(onNext, onError);
    }

    @NonNull
    public final Subscription subscribe(final Action1<? super Cursor> onNext,
            final Action1<Throwable> onError, final Action0 onCompleted) {
        return mObservable.subscribe(onNext, onError, onCompleted);
    }

    @NonNull
    public final Subscription subscribe(Subscriber<? super Cursor> subscriber) {
        return mObservable.subscribe(subscriber);
    }

    @NonNull
    public final RxCursorLoader subscribeOn(Scheduler scheduler) {
        mObservable = mObservable.subscribeOn(scheduler);
        return this;
    }

    @NonNull
    public final RxCursorLoader observeOn(Scheduler scheduler) {
        mObservable = mObservable.observeOn(scheduler);
        return this;
    }

    /**
     * Returns wrapped {@link Observable}. Use this if you don't need {@link
     * #reloadWithNewQuery(Query)}
     *
     * @return wrapped {@link Observable}
     */
    public final Observable<Cursor> asObservable() {
        return mObservable;
    }

    /**
     * Changes {@link Query} and reloads
     *
     * @param query the new Query
     */
    public void reloadWithNewQuery(@NonNull final Query query) {
        mOnSubscribe.reloadWithNewQuery(query);
    }

    /**
     * Create a new {@link RxCursorLoader} instance.
     *
     * @param resolver {@link ContentResolver} to use
     * @param query    the {@link Query} to use
     * @return new {@link RxCursorLoader} instance.
     */
    @NonNull
    public static RxCursorLoader create(@NonNull final ContentResolver resolver,
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
        final Observable<Cursor> observable = Observable.create(onSubscribe)
                .doOnUnsubscribe(onSubscribe::release)
                .doOnCompleted(onSubscribe::release)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(c -> onSubscribe.closePreviousCursor());
        return new RxCursorLoader(observable, onSubscribe);
    }

    private static final class CursorLoaderOnSubscribe
            implements Observable.OnSubscribe<Cursor> {

        @NonNull
        private final ContentResolver mContentResolver;

        @NonNull
        private Query mQuery;

        private Subscriber<? super Cursor> mSubscriber;

        private ContentObserver mResolverObserver;

        private Cursor mPrevious;
        private Cursor mCursor;

        CursorLoaderOnSubscribe(@NonNull final ContentResolver resolver,
                @NonNull final Query query) {
            mContentResolver = resolver;
            mQuery = query;
        }

        @Override
        public void call(final Subscriber<? super Cursor> subscriber) {
            mSubscriber = subscriber;
            reload();
        }

        void closePreviousCursor() {
            if (mPrevious != null) {
                mPrevious.close();
                mPrevious = null;
            }
        }

        private void release() {
            if (mCursor != null) {
                if (mResolverObserver != null) {
                    mCursor.unregisterContentObserver(mResolverObserver);
                }
                mCursor.close();
                mCursor = null;
            }
            if (mPrevious != null) {
                mPrevious.close();
                mPrevious = null;
            }
            mSubscriber = null;
        }

        void reloadWithNewQuery(@NonNull final Query query) {
            mQuery = query;
            reload();
        }

        /**
         * Unregisters ContentObserver from previous Cursor, loads new {@link Cursor} and registers
         * ContentObserver on it. Previous cursor will be closed in {@link #closePreviousCursor()}
         *
         * This must be called from {@link #call(Subscriber)} thread
         */
        private synchronized void reload() {
            final Cursor old = mCursor;
            if (old != null && mResolverObserver != null) {
                old.unregisterContentObserver(mResolverObserver);
            }

            if (LOG) {
                Log.d(TAG, mQuery.toString());
            }

            final Cursor c = mContentResolver.query(
                    mQuery.contentUri,
                    mQuery.projection,
                    mQuery.selection,
                    mQuery.selectionArgs,
                    mQuery.sortOrder);

            mCursor = c;
            if (old != c) {
                mPrevious = old;
            }

            if (c != null) {
                c.registerContentObserver(getResolverObserver());
            }
            if (mSubscriber != null) {
                mSubscriber.onNext(c);
            }
        }

        /**
         * Creates the {@link ContentObserver} to observe {@link Cursor} changes.
         * It must be initialized from thread in which {@link #call(Subscriber)} is called
         *
         * @return the {@link ContentObserver} to observe {@link Cursor} changes.
         */
        @NonNull
        private ContentObserver getResolverObserver() {
            if (mResolverObserver == null) {
                final HandlerThread handlerThread = new HandlerThread(
                        "RxCursorLoader.ContentObserver");
                handlerThread.start();
                mResolverObserver = new ContentObserver(new Handler(handlerThread.getLooper())) {

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
