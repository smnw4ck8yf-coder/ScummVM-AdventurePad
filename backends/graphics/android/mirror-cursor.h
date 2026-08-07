#ifndef BACKENDS_GRAPHICS_ANDROID_MIRROR_CURSOR_H
#define BACKENDS_GRAPHICS_ANDROID_MIRROR_CURSOR_H

#include <cmath>

struct AndroidMirrorCursorPresentation {
	bool upperVisible;
	bool lowerVisible;
	float lowerX;
	float lowerY;
	float lowerWidth;
	float lowerHeight;
};

inline bool deriveAndroidMirrorCursorPresentation(float hotspotX, float hotspotY,
		float cursorLeft, float cursorTop, float cursorWidth, float cursorHeight,
		float splitY, float cropLeft, float cropTop, float cropRight, float cropBottom,
		float destinationX, float destinationY, float destinationWidth, float destinationHeight,
		AndroidMirrorCursorPresentation &presentation) {
	const bool valid = std::isfinite(hotspotX) && std::isfinite(hotspotY) &&
		std::isfinite(cursorLeft) && std::isfinite(cursorTop) &&
		std::isfinite(cursorWidth) && std::isfinite(cursorHeight) &&
		std::isfinite(splitY) && std::isfinite(cropLeft) && std::isfinite(cropTop) &&
		std::isfinite(cropRight) && std::isfinite(cropBottom) &&
		std::isfinite(destinationX) && std::isfinite(destinationY) &&
		std::isfinite(destinationWidth) && std::isfinite(destinationHeight) &&
		splitY > 0.0f && splitY < 1.0f && cropLeft >= 0.0f && cropTop >= 0.0f &&
		cropRight <= 1.0f && cropBottom <= 1.0f && cropLeft < cropRight &&
		cropTop < cropBottom && std::fabs(splitY - cropTop) <= 0.0001f &&
		destinationWidth > 0.0f && destinationHeight > 0.0f;
	if (!valid)
		return false;

	presentation.upperVisible = hotspotX >= 0.0f && hotspotX <= 1.0f &&
		hotspotY >= 0.0f && hotspotY < splitY;
	presentation.lowerVisible = hotspotX >= cropLeft && hotspotX <= cropRight &&
		hotspotY >= cropTop && hotspotY < cropBottom;
	const float cropWidth = cropRight - cropLeft;
	const float cropHeight = cropBottom - cropTop;
	presentation.lowerX = destinationX + (cursorLeft - cropLeft) * destinationWidth / cropWidth;
	presentation.lowerY = destinationY + (cursorTop - cropTop) * destinationHeight / cropHeight;
	presentation.lowerWidth = cursorWidth * destinationWidth / cropWidth;
	presentation.lowerHeight = cursorHeight * destinationHeight / cropHeight;
	return true;
}

#endif
