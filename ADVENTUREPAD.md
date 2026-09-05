# ScummVM-AdventurePad

ScummVM-AdventurePad is a modified ScummVM source tree used by
[AdventurePad](https://github.com/smnw4ck8yf-coder/AdventurePad), an Android
dual-screen launcher and control surface currently designed and tested for the
AYN Thor.

This is not an official ScummVM project. ScummVM is a separate upstream project;
its source, documentation, credits, and releases are available from
[scummvm/scummvm](https://github.com/scummvm/scummvm). This fork preserves the
upstream Git history and is based on upstream commit
`4edac15aae5e1fe475eb5d4767b4c5ece3636164`. It intentionally has not yet been
rebased or merged onto newer upstream code, because the current integration has
been hardware-tested on this base.

## AdventurePad integration

The fork adds the ScummVM side of AdventurePad's integration:

- dual-display rendering to a surface supplied by AdventurePad;
- a lower-display input bridge for pointer, mouse-button, scroll, and gamepad
  events;
- Split View cropping and upper/lower presentation behavior;
- launcher and game-library operations used by AdventurePad;
- save-capability, resume, load-dialog, and configured-target removal bridges;
- optional artwork surrounding gameplay on the upper display.

AdventurePad currently targets the debug application ID
`org.scummvm.scummvm.debug`. Stock ScummVM is not interchangeable with this
build: it does not implement the AdventurePad bridge, and its package/signature
combination does not satisfy the explicit integration contract.

## Build and release status

The currently verified build is an `arm64-v8a` Android debug APK. The checked-in
source does not yet contain a complete dependency bootstrap: Oboe 1.10.0 and
libpng 1.6.43 are identified, while the local FLAC installation reports 1.5.0
but its source/bootstrap provenance has not been captured. See
[doc/adventurepad-android-build.md](doc/adventurepad-android-build.md) for the
verified build shape and unresolved reproducibility work.

AdventurePad and this APK must be signed by the same certificate for their
signature-protected services and providers to interoperate. No release signing
configuration is provided by this fork.

ScummVM and this modified tree are distributed under the GNU General Public
License; see [COPYING](COPYING). Any distributed modified APK must be accompanied
by the corresponding source in the manner required by the GPL. A future
AdventurePad release that ships an APK from this fork should name the exact fork
commit used to build it.
