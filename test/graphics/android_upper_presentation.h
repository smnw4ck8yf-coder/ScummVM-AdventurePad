#include <cxxtest/TestSuite.h>

#include "backends/graphics/android/upper-presentation.h"

class AndroidUpperPresentationTestSuite : public CxxTest::TestSuite {
public:
	void testBottomSplitProducesExactUpperComplement() {
		AndroidUpperPresentationCrop crop;
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0.75f, 1, 1, crop), kUpperPresentationExpanded);
		TS_ASSERT_DELTA(crop.left, 0.0f, 0.0001f);
		TS_ASSERT_DELTA(crop.top, 0.0f, 0.0001f);
		TS_ASSERT_DELTA(crop.right, 1.0f, 0.0001f);
		TS_ASSERT_DELTA(crop.bottom, 0.75f, 0.0001f);
	}

	void testUnsupportedAndDegenerateComplements() {
		AndroidUpperPresentationCrop crop;
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0.2f, 0.2f, 0.8f, 0.8f, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0.1f, 0.75f, 1, 1, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0, 1, 0.25f, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0, 0.25f, 1, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0.01f, 1, 1, crop),
			kUpperPresentationInvalidCrop);
	}
};
