# teleproto3 pre-built libraries

`{ABI}/libteleproto3.a` — built by `teleproto3/ci/build-android.sh`.

To rebuild:
```bash
cd /path/to/teleproto3
./ci/build-android.sh \
  --ndk $ANDROID_NDK_ROOT \
  --boringssl /path/to/Telegram-Android/TMessagesProj/jni/boringssl \
  --out /path/to/Telegram-Android/TMessagesProj/jni/teleproto3
```
