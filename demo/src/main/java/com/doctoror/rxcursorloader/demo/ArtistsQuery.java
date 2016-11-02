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
package com.doctoror.rxcursorloader.demo;

import com.doctoror.rxcursorloader.RxCursorLoader;

import android.provider.MediaStore;

/**
 * Created by Yaroslav Mytkalyk on 17.10.16.
 */

final class ArtistsQuery {

    private ArtistsQuery() {
        throw new UnsupportedOperationException();
    }

    static final int COLUMN_ID = 0;
    static final int COLUMN_NUMBER_OF_ALBUMS = 1;
    static final int COLUMN_ARTIST = 2;

    static RxCursorLoader.Query mQuery = new RxCursorLoader.Query.Builder()
            .setContentUri(MediaStore.Audio.Artists.INTERNAL_CONTENT_URI)
            .setProjection(new String[]{
                    MediaStore.Audio.Artists._ID,
                    MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                    MediaStore.Audio.Artists.ARTIST
            })
            .setSortOrder(MediaStore.Audio.Artists.ARTIST)
            .create();
}
