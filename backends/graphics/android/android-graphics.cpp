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

// Allow use of stuff in <time.h>
#define FORBIDDEN_SYMBOL_EXCEPTION_time_h

// Disable printf override in common/forbidden.h to avoid
// clashes with log.h from the Android SDK.
// That header file uses
//   __attribute__ ((format(printf, 3, 4)))
// which gets messed up by our override mechanism; this could
// be avoided by either changing the Android SDK to use the equally
// legal and valid
//   __attribute__ ((format(printf, 3, 4)))
// or by refining our printf override to use a varadic macro
// (which then wouldn't be portable, though).
// Anyway, for now we just disable the printf override globally
// for the Android port
#define FORBIDDEN_SYMBOL_EXCEPTION_printf

#include "backends/platform/android/android.h"
#include "backends/platform/android/jni-android.h"
#include "backends/graphics/android/android-graphics.h"
#include "backends/graphics/android/upper-presentation.h"
#include "backends/graphics/opengl/pipelines/pipeline.h"
#include "backends/graphics/opengl/renderer3d.h"
#include "backends/graphics/opengl/texture.h"

#include "graphics/blit.h"
#include "graphics/managed_surface.h"
#include "graphics/opengl/debug.h"

#include <cmath>

namespace {

int mirrorOrientation(Common::RotationMode rotation) {
	switch (rotation) {
	case Common::kRotation90: return 1;
	case Common::kRotation180: return 2;
	case Common::kRotation270: return 3;
	default: return 0;
	}
}

} // End of anonymous namespace

//
// AndroidGraphicsManager
//
AndroidGraphicsManager::AndroidGraphicsManager() :
	_touchcontrols(nullptr),
	_mirrorTarget(),
	_mirrorGeneration(0),
	_mirrorSourceState(-1),
	_mirrorDiagnosticFramesRemaining(0),
	_mirrorSourceWidth(0),
	_mirrorSourceHeight(0),
	_mirrorSourceOrientation(0),
	_mirrorGeometryGeneration(0),
	_mirrorCropLeft(0.0f),
	_mirrorCropTop(0.0f),
	_mirrorCropRight(1.0f),
	_mirrorCropBottom(1.0f),
	_pendingCropAckGeneration(0),
	_pendingCropAckGeometryGeneration(0),
	_upperPresentationExpanded(false),
	_upperGameplayLeft(0.0f),
	_upperGameplayTop(0.0f),
	_upperGameplayRight(1.0f),
	_upperGameplayBottom(1.0f),
	_pendingModeAckGeneration(0),
	_pendingModeAckGeometryGeneration(0),
	_pendingModeAckResult(0),
	_reportedSkinViewportLeft(-1),
	_reportedSkinViewportTop(-1),
	_reportedSkinViewportRight(-1),
	_reportedSkinViewportBottom(-1),
	_reportedSkinViewportWidth(-1),
	_reportedSkinViewportHeight(-1),
	_reportedMirrorCursorX(-1),
	_reportedMirrorCursorY(-1),
	_reportedMirrorCursorVisible(false),
	_reportedMirrorCursorGeometryGeneration(0),
	_mirrorRefreshFramePending(false),
	_old_touch_mode(OSystem_Android::TOUCH_MODE_TOUCHPAD) {
	ENTER();

	// Initialize our OpenGL ES context.
	initSurface();

	_rendering3d = (_renderer3d != nullptr);
	// maybe in 3D, not in GUI
	dynamic_cast<OSystem_Android *>(g_system)->applyTouchSettings(_rendering3d, false);
	dynamic_cast<OSystem_Android *>(g_system)->applyOrientationSettings();
}

AndroidGraphicsManager::~AndroidGraphicsManager() {
	ENTER();

	deinitSurface();

	delete _touchcontrols;
}

void AndroidGraphicsManager::initSurface() {
	LOGD("initializing 2D surface");

	assert(!JNI::haveSurface());
	if (!JNI::initSurface()) {
		error("JNI::initSurface failed");
	}

	if (JNI::egl_bits_per_pixel == 16) {
		// We default to RGB565 and RGBA5551 which is closest to what we setup in Java side
		notifyContextCreate(OpenGL::kContextGLES2,
				new OpenGL::Backbuffer(),
				Graphics::PixelFormat(2, 5, 6, 5, 0, 11, 5, 0, 0),
				Graphics::PixelFormat(2, 5, 5, 5, 1, 11, 6, 1, 0));
	} else {
		// If not 16, this must be 24 or 32 bpp so make use of them
		notifyContextCreate(OpenGL::kContextGLES2,
				new OpenGL::Backbuffer(),
				OpenGL::Texture::getRGBPixelFormat(),
				OpenGL::Texture::getRGBAPixelFormat()
		);
	}

	if (_touchcontrols) {
		_touchcontrols->recreate();
		_touchcontrols->updateGLTexture();
	} else {
		_touchcontrols = createSurface(_defaultFormatAlpha);
	}
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().setDrawer(
	    this, JNI::egl_surface_width, JNI::egl_surface_height);

	handleResize(JNI::egl_surface_width, JNI::egl_surface_height);
}

void AndroidGraphicsManager::deinitSurface() {
	if (!JNI::haveSurface())
		return;

	LOGD("deinitializing 2D surface");

	// Deregister us from touch control
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().setDrawer(
	    nullptr, 0, 0);
	if (_touchcontrols) {
		_touchcontrols->destroy();
	}

	notifyContextDestroy();

	JNI::deinitSurface();
}

void AndroidGraphicsManager::resizeSurface() {

	// If we had lost surface just init it again
	if (!JNI::haveSurface()) {
		initSurface();
		return;
	}

	// Recreate the EGL surface, context is preserved
	JNI::deinitSurface();
	if (!JNI::initSurface()) {
		error("JNI::initSurface failed");
	}

	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().setDrawer(
	    this, JNI::egl_surface_width, JNI::egl_surface_height);

	handleResize(JNI::egl_surface_width, JNI::egl_surface_height);
}


void AndroidGraphicsManager::updateScreen() {
	//ENTER();

	if (!JNI::haveSurface())
		return;

	// Attach and detach requests arrive on Android's main thread, but EGL
	// lifecycle work is consumed here on ScummVM's render thread.
	JNI::updateMirrorSurface();
	updateMirrorSourceGeometry();
	updateUpperPresentation();

	// Sets _forceRedraw if needed
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().beforeDraw();

	OpenGLGraphicsManager::updateScreen();
	updateSkinSurroundViewport();
	if (_pendingModeAckGeneration > 0) {
		const char *diagnostic = _pendingModeAckResult == 1 ? "Full-frame upper presentation applied" :
			_pendingModeAckResult == 2 ? "Expanded upper presentation applied" :
			_pendingModeAckResult == 3 ? "Crop shape cannot expand upper presentation" :
			_pendingModeAckResult == 6 ? "Renderer rotation cannot safely expand upper presentation" :
			"Upper presentation crop is invalid";
		JNI::reportUpperPresentationAck(_pendingModeAckResult, _pendingModeAckGeneration,
				_pendingModeAckGeometryGeneration, diagnostic);
		_pendingModeAckGeneration = 0;
		_pendingModeAckResult = 0;
	}
	renderMirrorSurface();
	_mirrorRefreshFramePending = false;
}

void AndroidGraphicsManager::updateSkinSurroundViewport() {
	Common::Rect viewport = getPresentationGameRect();
	if (_overlayVisible || viewport.isEmpty())
		viewport = Common::Rect(0, 0, _windowWidth, _windowHeight);
	if (viewport.left == _reportedSkinViewportLeft && viewport.top == _reportedSkinViewportTop &&
			viewport.right == _reportedSkinViewportRight && viewport.bottom == _reportedSkinViewportBottom &&
			_windowWidth == _reportedSkinViewportWidth && _windowHeight == _reportedSkinViewportHeight)
		return;
	_reportedSkinViewportLeft = viewport.left;
	_reportedSkinViewportTop = viewport.top;
	_reportedSkinViewportRight = viewport.right;
	_reportedSkinViewportBottom = viewport.bottom;
	_reportedSkinViewportWidth = _windowWidth;
	_reportedSkinViewportHeight = _windowHeight;
	JNI::reportAdventurePadGameViewport(viewport.left, viewport.top, viewport.right, viewport.bottom,
		_windowWidth, _windowHeight);
}

void AndroidGraphicsManager::handleMirrorLifecycleChange() {
	_mirrorRefreshFramePending = true;
	_forceRedraw = true;
	updateScreen();
}

void AndroidGraphicsManager::updateMirrorSourceGeometry() {
	int sourceWidth = 0;
	int sourceHeight = 0;
	int capability = 0;
	if (_gameScreen) {
		sourceWidth = _gameScreen->getGLTexture().getLogicalWidth();
		sourceHeight = _gameScreen->getGLTexture().getLogicalHeight();
		capability = sourceWidth > 0 && sourceHeight >= 4 ? 1 : 0;
	}
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	else if (_renderer3d && _renderer3d->hasTexture()) {
		sourceWidth = _renderer3d->getGLTexture().getLogicalWidth();
		sourceHeight = _renderer3d->getGLTexture().getLogicalHeight();
		capability = sourceWidth > 0 && sourceHeight >= 4 ? 1 : 0;
	}
#endif
	if (!capability) {
		sourceWidth = 0;
		sourceHeight = 0;
	}
	const int orientation = mirrorOrientation(_rotationMode);
	// Target changes advance the Java protocol generation without changing source dimensions.
	// Refresh the native validation cache before consuming queued CROP or MODE requests.
	const int64 publishedGeneration = _mirrorRefreshFramePending ?
			JNI::mirrorGeometryGeneration() : _mirrorGeometryGeneration;
	const bool logicalGeometryChanged = publishedGeneration > 0 &&
			publishedGeneration != _mirrorGeometryGeneration;
	if (_mirrorSourceWidth == sourceWidth && _mirrorSourceHeight == sourceHeight &&
			_mirrorSourceOrientation == orientation && !logicalGeometryChanged)
		return;
	const bool sourceGeometryChanged = _mirrorSourceWidth != sourceWidth ||
			_mirrorSourceHeight != sourceHeight || _mirrorSourceOrientation != orientation;
	_mirrorSourceWidth = sourceWidth;
	_mirrorSourceHeight = sourceHeight;
	_mirrorSourceOrientation = orientation;
	_mirrorGeometryGeneration = sourceGeometryChanged ?
			JNI::reportMirrorSourceGeometry(sourceWidth, sourceHeight, capability, orientation) :
			publishedGeneration;
	_mirrorCropLeft = _mirrorCropTop = 0.0f;
	_mirrorCropRight = _mirrorCropBottom = 1.0f;
	_upperPresentationExpanded = false;
	_pendingCropAckGeneration = 0;
	_pendingModeAckGeneration = 0;
	_forceRedraw = true;
}

void AndroidGraphicsManager::updateUpperPresentation() {
	if (!JNI::haveMirrorSurface() && _upperPresentationExpanded) {
		_upperPresentationExpanded = false;
		_forceRedraw = true;
	}

	int mode = 0;
	int64 modeGeneration = 0;
	int64 geometryGeneration = 0;
	float left = 0.0f, top = 0.0f, right = 1.0f, bottom = 1.0f;
	if (!JNI::updateUpperPresentation(mode, modeGeneration, geometryGeneration,
			left, top, right, bottom))
		return;

	int result = 1;
	AndroidUpperPresentationCrop gameplay = { 0.0f, 0.0f, 1.0f, 1.0f };
	if (mode == 1) {
		if (_rotationMode != Common::kRotationNormal) {
			result = 6;
		} else if (_mirrorSourceWidth <= 0 || _mirrorSourceHeight <= 0 ||
			(modeGeneration > 0 && geometryGeneration != _mirrorGeometryGeneration)) {
			result = kUpperPresentationInvalidCrop;
		} else {
			result = deriveAndroidUpperGameplayCrop(left, top, right, bottom, gameplay);
		}
	}

	_upperPresentationExpanded = result == kUpperPresentationExpanded;
	if (_upperPresentationExpanded) {
		_upperGameplayLeft = gameplay.left;
		_upperGameplayTop = gameplay.top;
		_upperGameplayRight = gameplay.right;
		_upperGameplayBottom = gameplay.bottom;
	} else {
		_upperGameplayLeft = _upperGameplayTop = 0.0f;
		_upperGameplayRight = _upperGameplayBottom = 1.0f;
	}
	_forceRedraw = true;
	if (modeGeneration > 0) {
		_pendingModeAckGeneration = modeGeneration;
		_pendingModeAckGeometryGeneration = _mirrorGeometryGeneration;
		_pendingModeAckResult = result;
	}
}

Common::Rect AndroidGraphicsManager::getPresentationGameRect() const {
	if (!_upperPresentationExpanded || _overlayVisible || _gameDrawRect.isEmpty())
		return _gameDrawRect;
	const float cropWidth = _upperGameplayRight - _upperGameplayLeft;
	const float cropHeight = _upperGameplayBottom - _upperGameplayTop;
	if (cropWidth <= 0.0f || cropHeight <= 0.0f || _windowWidth <= 0 || _windowHeight <= 0)
		return _gameDrawRect;
	const float originalAspect = (float)_gameDrawRect.width() / _gameDrawRect.height();
	const float gameplayAspect = originalAspect * cropWidth / cropHeight;
	int width = _windowWidth;
	int height = MAX(1, (int)(width / gameplayAspect));
	if (height > _windowHeight) {
		height = _windowHeight;
		width = MAX(1, (int)(height * gameplayAspect));
	}
	const int left = (_windowWidth - width) / 2;
	const int top = (_windowHeight - height) / 2;
	return Common::Rect(left, top, left + width, top + height);
}

bool AndroidGraphicsManager::getPresentationTextureCrop(GLfloat &left, GLfloat &top,
		GLfloat &right, GLfloat &bottom) const {
	if (!_upperPresentationExpanded || _overlayVisible)
		return false;
	left = _upperGameplayLeft;
	top = _upperGameplayTop;
	right = _upperGameplayRight;
	bottom = _upperGameplayBottom;
	return true;
}

bool AndroidGraphicsManager::transformCursorForPresentation(GLfloat &x, GLfloat &y,
		GLfloat &width, GLfloat &height) const {
	if (!_upperPresentationExpanded || _overlayVisible)
		return true;
	if (_gameDrawRect.isEmpty())
		return false;
	const float hotspotX = ((float)_cursorX - _gameDrawRect.left) / _gameDrawRect.width();
	const float hotspotY = ((float)_cursorY - _gameDrawRect.top) / _gameDrawRect.height();
	if (hotspotX < _upperGameplayLeft || hotspotX > _upperGameplayRight ||
		hotspotY < _upperGameplayTop || hotspotY >= _upperGameplayBottom)
		return false;
	const Common::Rect destination = getPresentationGameRect();
	const float cropWidth = _upperGameplayRight - _upperGameplayLeft;
	const float cropHeight = _upperGameplayBottom - _upperGameplayTop;
	const float sourceX = (x - _gameDrawRect.left) / _gameDrawRect.width();
	const float sourceY = (y - _gameDrawRect.top) / _gameDrawRect.height();
	x = destination.left + (sourceX - _upperGameplayLeft) * destination.width() / cropWidth;
	y = destination.top + (sourceY - _upperGameplayTop) * destination.height() / cropHeight;
	width *= destination.width() / (_gameDrawRect.width() * cropWidth);
	height *= destination.height() / (_gameDrawRect.height() * cropHeight);
	return true;
}

void AndroidGraphicsManager::renderMirrorSurface() {
	if (!JNI::haveMirrorSurface()) {
		_mirrorGeneration = 0;
		_mirrorSourceState = -1;
		return;
	}
	const int64 generation = JNI::mirrorSurfaceGeneration();
	if (_mirrorGeneration != generation) {
		_mirrorGeneration = generation;
		_mirrorSourceState = -1;
		_mirrorDiagnosticFramesRemaining = 8;
		_reportedMirrorCursorGeometryGeneration = 0;
	}

	const OpenGL::Texture *sourceTexture = nullptr;
	if (_gameScreen) {
		sourceTexture = &_gameScreen->getGLTexture();
	}
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	else if (_renderer3d && _renderer3d->hasTexture()) {
		sourceTexture = &_renderer3d->getGLTexture();
	}
#endif
	if (!sourceTexture || sourceTexture->getLogicalWidth() == 0 || sourceTexture->getLogicalHeight() < 4) {
		if (_mirrorSourceWidth != 0 || _mirrorSourceHeight != 0) {
			_mirrorSourceWidth = 0;
			_mirrorSourceHeight = 0;
			_mirrorSourceOrientation = mirrorOrientation(_rotationMode);
			_mirrorGeometryGeneration = JNI::reportMirrorSourceGeometry(0, 0, 0,
					mirrorOrientation(_rotationMode));
			_mirrorCropLeft = _mirrorCropTop = 0.0f;
			_mirrorCropRight = _mirrorCropBottom = 1.0f;
		}
		if (_mirrorSourceState != 0) {
			JNI::reportMirrorStatus(2, "Current renderer has no reusable game texture");
			_mirrorSourceState = 0;
		}
		return;
	}

	const int sourceWidth = sourceTexture->getLogicalWidth();
	const int sourceHeight = sourceTexture->getLogicalHeight();
	if (_mirrorSourceWidth != sourceWidth || _mirrorSourceHeight != sourceHeight) {
		_mirrorSourceWidth = sourceWidth;
		_mirrorSourceHeight = sourceHeight;
		_mirrorSourceOrientation = mirrorOrientation(_rotationMode);
		_mirrorGeometryGeneration = JNI::reportMirrorSourceGeometry(sourceWidth, sourceHeight, 1,
				mirrorOrientation(_rotationMode));
		_mirrorCropLeft = _mirrorCropTop = 0.0f;
		_mirrorCropRight = _mirrorCropBottom = 1.0f;
	}

	int64 requestedCropGeneration = 0;
	int64 requestedGeometryGeneration = 0;
	float requestedLeft = 0.0f;
	float requestedTop = 0.0f;
	float requestedRight = 1.0f;
	float requestedBottom = 1.0f;
	if (JNI::updateMirrorCrop(requestedCropGeneration, requestedGeometryGeneration,
			requestedLeft, requestedTop, requestedRight, requestedBottom)) {
		const bool valid = std::isfinite(requestedLeft) && std::isfinite(requestedTop) &&
			std::isfinite(requestedRight) && std::isfinite(requestedBottom) &&
			requestedLeft >= 0.0f && requestedTop >= 0.0f && requestedRight <= 1.0f &&
			requestedBottom <= 1.0f && requestedLeft < requestedRight &&
			requestedTop < requestedBottom && requestedRight - requestedLeft >= 0.05f &&
			requestedBottom - requestedTop >= 0.05f &&
			(requestedCropGeneration == 0 || requestedGeometryGeneration == _mirrorGeometryGeneration);
		if (valid) {
			_mirrorCropLeft = requestedLeft;
			_mirrorCropTop = requestedTop;
			_mirrorCropRight = requestedRight;
			_mirrorCropBottom = requestedBottom;
			_pendingCropAckGeneration = requestedCropGeneration;
			_pendingCropAckGeometryGeneration = requestedGeometryGeneration;
		} else {
			_mirrorCropLeft = _mirrorCropTop = 0.0f;
			_mirrorCropRight = _mirrorCropBottom = 1.0f;
			if (requestedCropGeneration > 0) {
				JNI::reportMirrorCropAck(4, requestedCropGeneration, _mirrorGeometryGeneration,
						"Native crop validation rejected the rectangle");
			}
			_pendingCropAckGeneration = 0;
		}
	}
	if (_mirrorDiagnosticFramesRemaining > 0) {
		const bool textureValid = glIsTexture(sourceTexture->getGLTexture()) == GL_TRUE;
		const bool uploadPending = _gameScreen && _gameScreen->isDirty();
		const GLenum textureError = glGetError();
		if (!textureValid || uploadPending || textureError != GL_NO_ERROR) {
			JNI::failMirrorSurface("Reusable source texture is not valid in the primary context");
			return;
		}
	}

	const int surfaceWidth = JNI::mirrorSurfaceWidth();
	const int surfaceHeight = JNI::mirrorSurfaceHeight();
	if (surfaceWidth <= 0 || surfaceHeight <= 0) {
		JNI::failMirrorSurface("Mirror surface dimensions became invalid");
		return;
	}

#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	const bool restore3D = _renderer3d != nullptr;
	if (restore3D)
		_renderer3d->leave3D();
#endif

	OpenGL::Pipeline *pipeline = getPipeline();
	if (!pipeline) {
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
		if (restore3D)
			_renderer3d->enter3D();
#endif
		JNI::failMirrorSurface("OpenGL pipeline is unavailable");
		return;
	}
	OpenGL::Pipeline::disable();
	OpenGL::Framebuffer *primaryTarget = pipeline->setFramebuffer(&_mirrorTarget);
	_mirrorTarget.setSize(surfaceWidth, surfaceHeight);

	if (!JNI::makeMirrorSurfaceCurrent()) {
		const bool primaryRestored = JNI::failMirrorSurface("Could not make the mirror EGLSurface current");
		pipeline->setFramebuffer(primaryTarget);
		if (primaryRestored)
			pipeline->activate();
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
		if (restore3D && primaryRestored)
			_renderer3d->enter3D();
#endif
		return;
	}

	pipeline->activate();
	_mirrorTarget.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	_mirrorTarget.enableScissorTest(false);
	_mirrorTarget.enableBlend(OpenGL::Framebuffer::kBlendModeOpaque);
	pipeline->setColor(1.0f, 1.0f, 1.0f, 1.0f);
	GL_CALL(glClear(GL_COLOR_BUFFER_BIT));

	const int cropLeft = MAX(0, MIN(sourceWidth - 1, (int)std::floor(_mirrorCropLeft * sourceWidth)));
	// Split payloads are snapped to a source row by AdventurePad. Round here too so
	// lower and upper regions share the exact same row boundary without overlap.
	const int cropTop = MAX(0, MIN(sourceHeight - 1, (int)std::lround(_mirrorCropTop * sourceHeight)));
	const int cropRight = MAX(cropLeft + 1, MIN(sourceWidth, (int)std::ceil(_mirrorCropRight * sourceWidth)));
	const int cropBottom = MAX(cropTop + 1, MIN(sourceHeight, (int)std::ceil(_mirrorCropBottom * sourceHeight)));
	// Split View intentionally maps the configured crop across the complete lower
	// surface. X and Y scales are independent; do not aspect-fit or add padding.
	const int destinationX = 0;
	const int destinationY = 0;
	const int destinationWidth = surfaceWidth;
	const int destinationHeight = surfaceHeight;
	const GLenum beforeDrawError = glGetError();
	// Clip by interpolating within the texture's own canonical flip/rotation
	// coordinates. Never assume an unflipped 0..1 orientation here.
	pipeline->drawTextureNormalizedCrop(*sourceTexture, destinationX, destinationY,
			destinationWidth, destinationHeight,
			(float)cropLeft / sourceWidth, (float)cropTop / sourceHeight,
			(float)cropRight / sourceWidth, (float)cropBottom / sourceHeight);
	const bool cursorVisible = _cursorVisible && _cursor && !_overlayVisible && !_gameDrawRect.isEmpty();
	int cursorSourceX = 0;
	int cursorSourceY = 0;
	if (cursorVisible) {
		cursorSourceX = CLIP<int>((int)std::floor(
				((float)_cursorX - _gameDrawRect.left) * sourceWidth / _gameDrawRect.width()),
				0, sourceWidth - 1);
		cursorSourceY = CLIP<int>((int)std::floor(
				((float)_cursorY - _gameDrawRect.top) * sourceHeight / _gameDrawRect.height()),
				0, sourceHeight - 1);
	}
	if (cursorSourceX != _reportedMirrorCursorX || cursorSourceY != _reportedMirrorCursorY ||
			cursorVisible != _reportedMirrorCursorVisible ||
			_mirrorGeometryGeneration != _reportedMirrorCursorGeometryGeneration) {
		JNI::reportMirrorCursor(cursorSourceX, cursorSourceY, cursorVisible,
				_mirrorGeometryGeneration);
		_reportedMirrorCursorX = cursorSourceX;
		_reportedMirrorCursorY = cursorSourceY;
		_reportedMirrorCursorVisible = cursorVisible;
		_reportedMirrorCursorGeometryGeneration = _mirrorGeometryGeneration;
	}
	const GLenum drawError = glGetError();

	const bool swapped = beforeDrawError == GL_NO_ERROR && drawError == GL_NO_ERROR && JNI::swapMirrorSurface();
	OpenGL::Pipeline::disable();
	bool primaryRestored = JNI::makePrimarySurfaceCurrent();
	if (!primaryRestored) {
		primaryRestored = JNI::failMirrorSurface("Could not restore the primary EGLSurface");
	} else if (!swapped) {
		primaryRestored = JNI::failMirrorSurface(beforeDrawError == GL_NO_ERROR && drawError == GL_NO_ERROR ?
				"Mirror buffer swap failed" : "Mirror draw produced a GL error");
	}
	pipeline->setFramebuffer(primaryTarget);
	if (primaryRestored)
		pipeline->activate();

#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	if (restore3D && primaryRestored)
		_renderer3d->enter3D();
#endif
	if (_mirrorDiagnosticFramesRemaining > 0)
		--_mirrorDiagnosticFramesRemaining;

	if (!swapped || !primaryRestored) {
		if (_pendingCropAckGeneration > 0) {
			JNI::reportMirrorCropAck(2, _pendingCropAckGeneration,
					_pendingCropAckGeometryGeneration, "Crop draw failed; full frame restored");
			_pendingCropAckGeneration = 0;
		}
		_mirrorCropLeft = _mirrorCropTop = 0.0f;
		_mirrorCropRight = _mirrorCropBottom = 1.0f;
		_mirrorSourceState = -1;
		return;
	}
	if (_pendingCropAckGeneration > 0) {
		JNI::reportMirrorCropAck(1, _pendingCropAckGeneration,
				_pendingCropAckGeometryGeneration, "Crop applied to live mirror");
		_pendingCropAckGeneration = 0;
	}

	if (_mirrorSourceState != 1) {
		JNI::reportMirrorStatus(1, "Live crop-capable texture mirror supported");
		_mirrorSourceState = 1;
	}
}

void AndroidGraphicsManager::displayMessageOnOSD(const Common::U32String &msg) {
	ENTER("%s", msg.encode().c_str());

	JNI::displayMessageOnOSD(msg);
}

void AndroidGraphicsManager::recalculateDisplayAreas() {
	Common::Rect oldDrawRect = _activeArea.drawRect;

	OpenGLGraphicsManager::recalculateDisplayAreas();

	int offsetX = _activeArea.drawRect.left - oldDrawRect.left;
	int offsetY = _activeArea.drawRect.top - oldDrawRect.top;

	int newX = _cursorX + offsetX;
	int newY = _cursorY + offsetY;

	newX = CLIP<int16>(newX, _activeArea.drawRect.left, _activeArea.drawRect.right);
	newY = CLIP<int16>(newY, _activeArea.drawRect.top, _activeArea.drawRect.bottom);

	setMousePosition(newX, newY);
}

void AndroidGraphicsManager::showOverlay(bool inGUI) {
	if (_overlayVisible && inGUI == _overlayInGUI)
		return;

	// Don't change touch mode when not changing mouse coordinates
	if (inGUI) {
		_old_touch_mode = JNI::getTouchMode();
		// maybe in 3D, in overlay
		dynamic_cast<OSystem_Android *>(g_system)->applyTouchSettings(_renderer3d != nullptr, true);
		dynamic_cast<OSystem_Android *>(g_system)->applyOrientationSettings();
	} else if (_overlayInGUI) {
		// Restore touch mode active before overlay was shown
		JNI::setTouchMode(_old_touch_mode);
	}

	OpenGL::OpenGLGraphicsManager::showOverlay(inGUI);
}

void AndroidGraphicsManager::hideOverlay() {
	if (!_overlayVisible)
		return;

	if (_overlayInGUI) {
		// Restore touch mode active before overlay was shown
		JNI::setTouchMode(_old_touch_mode);
		dynamic_cast<OSystem_Android *>(g_system)->applyOrientationSettings();
	}

	OpenGL::OpenGLGraphicsManager::hideOverlay();
}

float AndroidGraphicsManager::getHiDPIScreenFactor() const {
	JNI::DPIValues dpi;
	JNI::getDPI(dpi);
	// Scale down the Android factor else the GUI is too big and
	// there is not much options to go smaller
	return dpi[2] / 1.2f;
}

bool AndroidGraphicsManager::loadVideoMode(uint requestedWidth, uint requestedHeight, bool resizable, int antialiasing) {
	ENTER("%d, %d, %d, %d", requestedWidth, requestedHeight, resizable, antialiasing);

	// As GLES2 provides FBO, OpenGL graphics manager must ask us for a resizable surface
	assert(resizable);
	if (antialiasing != 0) {
		warning("Requesting antialiased video mode while not available");
	}

	const bool render3d = (_renderer3d != nullptr);
	if (_rendering3d != render3d) {
		_rendering3d = render3d;
		// 3D status changed: refresh the touch mode
		applyTouchSettings();
	}

	// We get this whenever a new resolution is requested. Since Android is
	// using a fixed output size we do nothing like that here.
	return true;
}

void AndroidGraphicsManager::refreshScreen() {
	//ENTER();

	// Last minute draw of touch controls
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().draw();

	JNI::swapBuffers();
}

void AndroidGraphicsManager::applyTouchSettings() const {
	// maybe in 3D, maybe in GUI
	dynamic_cast<OSystem_Android *>(g_system)->applyTouchSettings(_renderer3d != nullptr, _overlayVisible && _overlayInGUI);
}

void AndroidGraphicsManager::syncVirtkeyboardState(bool virtkeybd_on) {
	_screenAlign = SCREEN_ALIGN_CENTER;
	if (virtkeybd_on) {
		_screenAlign |= SCREEN_ALIGN_TOP;
	} else {
		_screenAlign |= SCREEN_ALIGN_MIDDLE;
	}
	recalculateDisplayAreas();
	_forceRedraw = true;
}

void AndroidGraphicsManager::touchControlInitSurface(const Graphics::ManagedSurface &surf) {
	if (_touchcontrols->getWidth() == (uint)surf.w && _touchcontrols->getHeight() == (uint)surf.h) {
		return;
	}

	_touchcontrols->allocate(surf.w, surf.h);
	Graphics::Surface *dst = _touchcontrols->getSurface();

	Graphics::crossBlit(
			(byte *)dst->getPixels(), (const byte *)surf.getPixels(),
			dst->pitch, surf.pitch,
			surf.w, surf.h,
			dst->format, surf.format);
	_touchcontrols->updateGLTexture();
}

void AndroidGraphicsManager::touchControlDraw(uint8 alpha, int16 x, int16 y, int16 w, int16 h, const Common::Rect &clip) {
	_targetBuffer->enableBlend(OpenGL::Framebuffer::kBlendModeTraditionalTransparency);
	OpenGL::Pipeline *pipeline = getPipeline();
	pipeline->activate();
	if (alpha != 255) {
		pipeline->setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
	}
	pipeline->drawTexture(_touchcontrols->getGLTexture(),
	                      x, y, w, h, clip);
	if (alpha != 255) {
		pipeline->setColor(1.0f, 1.0f, 1.0f, 1.0f);
	}
}

void AndroidGraphicsManager::touchControlNotifyChanged() {
	// Make sure we redraw the screen
	_forceRedraw = true;
}

bool AndroidGraphicsManager::notifyMousePosition(Common::Point &mouse) {
	mouse.x = CLIP<int16>(mouse.x, _activeArea.drawRect.left, _activeArea.drawRect.right);
	mouse.y = CLIP<int16>(mouse.y, _activeArea.drawRect.top, _activeArea.drawRect.bottom);

	setMousePosition(mouse.x, mouse.y);
	mouse = convertWindowToVirtual(mouse.x, mouse.y);

	return true;
}

bool AndroidGraphicsManager::notifyMousePositionVirtual(Common::Point &mouse) {
	if (_activeArea.width <= 0 || _activeArea.height <= 0)
		return false;
	mouse.x = CLIP<int16>(mouse.x, 0, _activeArea.width - 1);
	mouse.y = CLIP<int16>(mouse.y, 0, _activeArea.height - 1);
	const Common::Point window = convertVirtualToWindow(mouse.x, mouse.y);
	setMousePosition(window.x, window.y);
	return true;
}

WindowedGraphicsManager::Insets AndroidGraphicsManager::getSafeAreaInsets() const {
	return WindowedGraphicsManager::Insets{
		(int16)JNI::cutout_insets[0], (int16)JNI::cutout_insets[1],
		(int16)JNI::cutout_insets[2], (int16)JNI::cutout_insets[3]};
}
