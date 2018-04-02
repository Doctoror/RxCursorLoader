/*
 * Copyright (C) 2018 Yaroslav Mytkalyk
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
import android.support.annotation.NonNull;
import android.util.Log;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;

import static com.doctoror.rxcursorloader.RxCursorLoader.LOG;
import static com.doctoror.rxcursorloader.RxCursorLoader.TAG;

final class RxCursorLoaderSingleFactory {

    @NonNull
    static Single<Cursor> single(
            @NonNull final ContentResolver resolver,
            @NonNull final RxCursorLoader.Query query) {
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

    private static final class CursorLoaderOnSubscribeSingle
            implements SingleOnSubscribe<Cursor> {

        @NonNull
        private final ContentResolver mContentResolver;

        @NonNull
        private final RxCursorLoader.Query mQuery;

        CursorLoaderOnSubscribeSingle(
                @NonNull final ContentResolver resolver,
                @NonNull final RxCursorLoader.Query query) {
            mContentResolver = resolver;
            mQuery = query;
        }

        @Override
        public void subscribe(final SingleEmitter<Cursor> emitter) {
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
}
