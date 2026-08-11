/* ScummVM - Graphic Adventure Engine
 *
 * ScummVM is the legal property of its developers, whose names
 * are too numerous to list here. Please refer to the COPYRIGHT
 * file distributed with this source distribution.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

#ifndef BACKENDS_GRAPHICS_ANDROID_ANDROID_GRAPHICS_H
#define BACKENDS_GRAPHICS_ANDROID_ANDROID_GRAPHICS_H

#include "common/scummsys.h"
#include "backends/graphics/opengl/opengl-graphics.h"

#include "backends/platform/android/touchcontrols.h"

class AndroidGraphicsManager :
	public OpenGL::OpenGLGraphicsManager, public TouchControlsDrawer {
public:
	AndroidGraphicsManager();
	virtual ~AndroidGraphicsManager();

	void initSurface();
	void deinitSurface();
	void resizeSurface();

	WindowedGraphicsManager::Insets getSafeAreaInsets() const override;

	void updateScreen() override;
	void handleMirrorLifecycleChange();

	void displayMessageOnOSD(const Common::U32String &msg) override;

	bool notifyMousePosition(Common::Point &mouse);
	bool notifyMousePositionVirtual(Common::Point &mouse);
	Common::Point getMousePosition() { return Common::Point(_cursorX, _cursorY); }

	float getHiDPIScreenFactor() const override;

	void touchControlInitSurface(const Graphics::ManagedSurface &surf) override;
	void touchControlNotifyChanged() override;
	void touchControlDraw(uint8 alpha, int16 x, int16 y, int16 w, int16 h, const Common::Rect &clip) override;

	void syncVirtkeyboardState(bool virtkeybd_on);
	void applyTouchSettings() const;

protected:
	void recalculateDisplayAreas() override;
	Common::Rect getPresentationGameRect() const override;
	bool getPresentationTextureCrop(GLfloat &left, GLfloat &top,
			GLfloat &right, GLfloat &bottom) const override;
	bool transformCursorForPresentation(GLfloat &x, GLfloat &y,
			GLfloat &width, GLfloat &height) const override;
	void setSystemMousePosition(const int x, const int y) override {}

	void showOverlay(bool inGUI) override;
	void hideOverlay() override;


	bool loadVideoMode(uint requestedWidth, uint requestedHeight, bool resizable, int antialiasing) override;

	void refreshScreen() override;

private:
	void renderMirrorSurface();
	void updateMirrorSourceGeometry();
	void updateUpperPresentation();
	void updateSkinSurroundViewport();
	void logMirrorRenderTransition(const char *stage);
	void logMirrorTextureLifecycle(const char *stage);

	OpenGL::Surface *_touchcontrols;
	OpenGL::Backbuffer _mirrorTarget;
	int64 _mirrorGeneration;
	int _mirrorSourceState;
	int _mirrorDiagnosticFramesRemaining;
	int _mirrorSourceWidth;
	int _mirrorSourceHeight;
	int _mirrorSourceOrientation;
	int64 _mirrorGeometryGeneration;
	float _mirrorCropLeft;
	float _mirrorCropTop;
	float _mirrorCropRight;
	float _mirrorCropBottom;
	int64 _pendingCropAckGeneration;
	int64 _pendingCropAckGeometryGeneration;
	bool _upperPresentationExpanded;
	float _upperGameplayLeft;
	float _upperGameplayTop;
	float _upperGameplayRight;
	float _upperGameplayBottom;
	int64 _pendingModeAckGeneration;
	int64 _pendingModeAckGeometryGeneration;
	int _pendingModeAckResult;
	int _reportedSkinViewportLeft;
	int _reportedSkinViewportTop;
	int _reportedSkinViewportRight;
	int _reportedSkinViewportBottom;
	int _reportedSkinViewportWidth;
	int _reportedSkinViewportHeight;
	int _reportedMirrorCursorX;
	int _reportedMirrorCursorY;
	bool _reportedMirrorCursorVisible;
	int64 _reportedMirrorCursorGeometryGeneration;
	int _mirrorRenderLogCount;
	int _mirrorRefreshEventCount;
	bool _mirrorRefreshFramePending;
	bool _mirrorCursorFramePending;
	bool _awaitingFirstMirrorCursorMovement;
	int _mirrorTextureTraceSequence;
	int _mirrorTextureTraceLogCount;
	int _old_touch_mode;
	bool _rendering3d;
};

#endif
