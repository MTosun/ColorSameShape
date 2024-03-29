/*
 * Project: asqare
 * Copyright (C) 2008-2012 rdrr.labs@gmail.com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alfray.asqare;

import java.lang.reflect.Constructor;

import android.content.Context;
import android.graphics.Point;
import android.os.Vibrator;
import android.util.Log;
import android.widget.TextView;

import com.alfray.asqare.engine.AnimThread;
import com.alfray.asqare.engine.Board;
import com.alfray.asqare.engine.CellRegion;
import com.alfray.asqare.engine.Board.Cell;
import com.alfray.asqare.gameplay.Gameplay;
import com.alfray.asqare.prefs.PrefsValues;
import com.alfray.asqare.view.AsqareView;

public class AsqareContext {

    private static final String TAG = "Asqare.Context";

    private AsqareView mBoardView;
	private TextView mStatusView;
	private Gameplay mGameplay;
	private Board<? extends Cell> mBoard;
	private final AsqareActivity mActivity;
	private AnimThread mAnimThread;
	private PrefsValues mPrefsValues = new PrefsValues();

	public AsqareContext(AsqareActivity activity) {
		mActivity = activity;
	}

	public AsqareActivity getActivity() {
		return mActivity;
	}

	public void setBoardView(AsqareView mainView) {
		mBoardView = mainView;
	}

	public AsqareView getBoardView() {
		return mBoardView;
	}

	public void setStatusView(TextView statusView) {
		mStatusView = statusView;
	}

	public TextView getStatusView() {
		return mStatusView;
	}

	public Gameplay getGameplay() {
		return mGameplay;
	}

	public void setBoard(Board<? extends Cell> board) {
		mBoard = board;
		if (mBoardView != null) mBoardView.onBoardChanged();
	}

	public Board<? extends Cell> getBoard() {
		return mBoard;
	}

	public void invalidateCell(Cell cell) {
		if (mBoardView != null) mBoardView.invalidateCell(cell);
	}

	public void invalidateRegion(CellRegion dirtyRegion) {
		if (mBoardView != null) mBoardView.invalidateRegion(dirtyRegion);
	}

	public void invalidateAll() {
		if (mBoardView != null) mBoardView.postInvalidate();
	}

	public boolean getCellForPoint(int pixel_x, int pixel_y, Point out_selection) {
		return mBoardView.getCellForPoint(pixel_x, pixel_y, out_selection);
	}

	public void setAnimThread(AnimThread actionThread) {
		mAnimThread = actionThread;
	}

	public AnimThread getAnimThread() {
		return mAnimThread;
	}

	public PrefsValues getPrefsValues() {
		return mPrefsValues;
	}

    /**
     * Changes the status text message.
     * <p/>
     * It is safe to call this from a non-UI thread.
     */
	public void setStatus(CharSequence charSequence) {
		if (mActivity != null) mActivity.setStatus(charSequence);
	}

	/**
	 * Starts the gameplay (if any) & the anim thread.
	 */
	public void startGameplay() {
		if (mGameplay != null) {
			if (mBoardView != null) mBoardView.setUiEventHandler(mGameplay);
			mGameplay.start();
			updateWindowTitle();
			if (mAnimThread != null) mAnimThread.start();
		}
	}

	/**
	 * Updates the window title with the title from the current gameplay, if any.
	 */
	public void updateWindowTitle() {
		if (mActivity != null && mGameplay != null) {
			String title = mGameplay.getName();
			if (title != null) mActivity.setTitle(title);
		}
	}

	/**
	 * Creates a ganeplay but does not start it.
	 * If the current gameplay is of the same instance, it is reused.
	 * <p/>
	 * As a side effect, it creates the anim thread before if needed since the
	 * constructor of the new gameplay might capture the thread reference.
	 *
	 * @param clazz A class that extends {@link Gameplay}.
	 * @param state An optional Activity.onCreate bundle to restore state.
	 * @return The new gameplay or the existing one if the same.
	 */
	public Gameplay createGameplay(Class<? extends Gameplay> clazz, String state) {
		if (mAnimThread == null) mAnimThread = new AnimThread(this);

		if (mGameplay != null) mGameplay.stop();

		if (clazz != null && !clazz.isInstance(mGameplay)) {
			mGameplay = instantiateGameplay(clazz);
    	}

		mGameplay.create(state);
		return mGameplay;
	}

	public Gameplay instantiateGameplay(Class<? extends Gameplay> clazz) {
		try {
			Constructor<? extends Gameplay> constructor;
			constructor = clazz.getConstructor(new Class<?>[] { AsqareContext.class } );
			return constructor.newInstance(new Object[] { this });
		} catch (Exception e) {
			Log.e(TAG, "Instantiate gameplay '" + clazz.getSimpleName() + "' failed.", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a gameplay for this class name (FQCN) but does not start it.
	 * <p/>
	 * If the class name cannot be found, or cannot be instantiated, this
	 * simply returns null. Otherwise it creates the class with the associated
	 * state.
	 *
	 * @param clazz The FQCN of a class that extends {@link Gameplay}.
	 * @param state An optional Activity.onCreate bundle to restore state.
	 * @return The new gameplay, an existing one, or null.
	 */
	@SuppressWarnings("unchecked")
	public Gameplay createGameplay(String class_name, String state) {
		if (class_name != null) {
			try {
				Class<?> c = getClass().getClassLoader().loadClass(class_name);
				return createGameplay((Class<? extends Gameplay>) c, state);
			} catch (ClassNotFoundException e) {
				Log.e(TAG, "Create gameplay '" + class_name + "' failed.", e);
			}
		}
		return null;
	}

	/**
	 * Returns true if this FQCN is a valid Gameplay class name with an
	 * adequate constructor.
	 */
	@SuppressWarnings("unchecked")
	public boolean validateGameplayClass(String class_name) {
		try {
			Class<? extends Gameplay> c = (Class<? extends Gameplay>) getClass().getClassLoader().loadClass(class_name);
			Constructor<? extends Gameplay> constructor;
			constructor = c.getConstructor(new Class<?>[] { AsqareContext.class } );
			return constructor != null;
		} catch (Exception e) {
			Log.e(TAG, "Create gameplay '" + class_name + "' failed.", e);
			return false;
		}
	}

	/**
	 * Notifies the current gameplay (if any) that preferences may have been changed
	 * by the user.
	 */
	public void onPrefsUpdated() {
		if (mGameplay != null) {
			mGameplay.onPrefsUpdated(mPrefsValues);
		}
	}

	public void vibrateTouch() {
		if (mPrefsValues.vibrateTouch() && mActivity != null) {
			Vibrator vib = (Vibrator) mActivity.getSystemService(Context.VIBRATOR_SERVICE);
			if (vib != null) {
				vib.vibrate(15 /* ms */);
			}
		}
	}

	public void vibrateSequence(int count) {
		if (mPrefsValues.vibrateSequence() && mActivity != null) {
			Vibrator vib = (Vibrator) mActivity.getSystemService(Context.VIBRATOR_SERVICE);
			if (vib != null) {
				vib.vibrate(count * 15 /* ms */);
			}
		}
	}
}
