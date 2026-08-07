#include <cxxtest/TestSuite.h>

#include "graphics/opengl/texture.h"

class OpenGLTextureCoordsTestSuite : public CxxTest::TestSuite {
public:
	void testCropRespectsAllCanonicalOrientations() {
		const GLfloat orientations[][8] = {
			{ 0, 0, 1, 0, 0, 1, 1, 1 },
			{ 0, 1, 1, 1, 0, 0, 1, 0 },
			{ 0, 1, 0, 0, 1, 1, 1, 0 },
			{ 1, 1, 0, 1, 1, 0, 0, 0 },
			{ 1, 0, 1, 1, 0, 0, 0, 1 }
		};
		for (uint orientation = 0; orientation < ARRAYSIZE(orientations); ++orientation) {
			GLfloat clipped[8];
			OpenGL::Texture::calculateClippedTexCoords(
				orientations[orientation], 0.25f, 0.5f, 0.75f, 1.0f, clipped);
			for (int component = 0; component < 8; ++component) {
				TS_ASSERT(clipped[component] >= 0.0f);
				TS_ASSERT(clipped[component] <= 1.0f);
			}
			const GLfloat expected =
				(orientations[orientation][0] * 0.75f + orientations[orientation][2] * 0.25f) * 0.5f +
				(orientations[orientation][4] * 0.75f + orientations[orientation][6] * 0.25f) * 0.5f;
			TS_ASSERT_DELTA(clipped[0], expected,
				0.0001f);
		}
	}
};
