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
import com.tbruyelle.rxpermissions.RxPermissions;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewAnimator;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Subscription;

public final class DemoActivity extends Activity {

    private static final int ANIMATOR_CHILD_PROGRESS = 0;
    private static final int ANIMATOR_CHILD_ERROR = 1;
    private static final int ANIMATOR_CHILD_EMPTY = 2;
    private static final int ANIMATOR_CHILD_LIST = 3;

    @BindView(R.id.animator)
    ViewAnimator mAnimator;

    @BindView(R.id.textError)
    TextView mErrorText;

    @BindView(android.R.id.list)
    ListView mListView;

    private Subscription mCursorSubscription;
    private ArtistsCursorAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        ButterKnife.bind(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAnimator.setDisplayedChild(ANIMATOR_CHILD_PROGRESS);
        new RxPermissions(this)
                .request(externalStoragePermission())
                .subscribe(granted -> {
                    if (granted) {
                        subscribe();
                    } else {
                        showError(getText(R.string.Permission_denied));
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAdapter != null) {
            mAdapter.swapCursor(null);
        }
        if (mCursorSubscription != null) {
            mCursorSubscription.unsubscribe();
            mCursorSubscription = null;
        }
    }

    private void showError(@NonNull final CharSequence message) {
        mErrorText.setText(message);
        mAnimator.setDisplayedChild(ANIMATOR_CHILD_ERROR);
    }

    private void subscribe() {
        mCursorSubscription = RxCursorLoader.create(getContentResolver(),
                ArtistsQuery.mQuery).subscribe(cursor -> {
            if (mAdapter == null) {
                mAdapter = new ArtistsCursorAdapter(DemoActivity.this, cursor);
                mListView.setAdapter(mAdapter);
            } else {
                mAdapter.swapCursor(cursor);
            }
            mAnimator.setDisplayedChild(mAdapter.isEmpty()
                    ? ANIMATOR_CHILD_EMPTY : ANIMATOR_CHILD_LIST);
        });
    }

    private static String externalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return PermissionsJellyBean.PERMISSION;
        }
        return PermissionsCompat.PERMISSION;
    }

    private static final class PermissionsCompat {

        static String PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static final class PermissionsJellyBean {

        static String PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;
    }
}
