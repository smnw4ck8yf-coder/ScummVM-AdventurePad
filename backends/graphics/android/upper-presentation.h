#ifndef BACKENDS_GRAPHICS_ANDROID_UPPER_PRESENTATION_H
#define BACKENDS_GRAPHICS_ANDROID_UPPER_PRESENTATION_H

#include <cmath>

struct AndroidUpperPresentationCrop {
	float left;
	float top;
	float right;
	float bottom;
};

enum AndroidUpperPresentationDerivation {
	kUpperPresentationExpanded = 2,
	kUpperPresentationUnsupportedShape = 3,
	kUpperPresentationInvalidCrop = 4
};

inline int deriveAndroidUpperGameplayCrop(float left, float top, float right, float bottom,
		AndroidUpperPresentationCrop &gameplay) {
	const float minimum = 0.05f;
	const float edgeTolerance = 0.0001f;
	const bool valid = std::isfinite(left) && std::isfinite(top) &&
		std::isfinite(right) && std::isfinite(bottom) &&
		left >= 0.0f && top >= 0.0f && right <= 1.0f && bottom <= 1.0f &&
		left < right && top < bottom && right - left >= minimum && bottom - top >= minimum;
	if (!valid)
		return kUpperPresentationInvalidCrop;

	const bool lowerFullWidthRegion = left <= edgeTolerance && right >= 1.0f - edgeTolerance &&
		bottom >= 1.0f - edgeTolerance;
	if (lowerFullWidthRegion) {
		gameplay = { 0.0f, 0.0f, 1.0f, top };
	} else {
		return kUpperPresentationUnsupportedShape;
	}

	if (gameplay.right - gameplay.left < minimum || gameplay.bottom - gameplay.top < minimum)
		return kUpperPresentationInvalidCrop;
	return kUpperPresentationExpanded;
}

#endif
