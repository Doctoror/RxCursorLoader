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

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by Yaroslav Mytkalyk on 31.10.16.
 */

public final class ArtistsCursorAdapter extends CursorAdapter {

    private final LayoutInflater mInflater;

    public ArtistsCursorAdapter(final Context context, final Cursor c) {
        super(context, c, FLAG_REGISTER_CONTENT_OBSERVER);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View newView(final Context context, final Cursor cursor, final ViewGroup viewGroup) {
        final View view = mInflater.inflate(R.layout.list_item_two_line, viewGroup, false);
        view.setTag(new ViewHolder(view));
        return view;
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        final ViewHolder vh = (ViewHolder) view.getTag();
        vh.text1.setText(cursor.getString(ArtistsQuery.COLUMN_ARTIST));

        final int albumsCount = cursor.getInt(ArtistsQuery.COLUMN_NUMBER_OF_ALBUMS);
        vh.text2.setText(context.getResources().getQuantityString(R.plurals.d_albums,
                albumsCount, albumsCount));
    }

    private static final class ViewHolder {

        TextView text1;
        TextView text2;

        ViewHolder(@NonNull final View view) {
            text1 = (TextView) view.findViewById(android.R.id.text1);
            text2 = (TextView) view.findViewById(android.R.id.text2);
        }
    }
}
