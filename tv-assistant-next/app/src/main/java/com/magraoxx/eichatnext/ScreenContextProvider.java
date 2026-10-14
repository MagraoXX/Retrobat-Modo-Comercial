package com.magraoxx.eichatnext;

public interface ScreenContextProvider {
    final class Context {
        public final byte[] screenshotJpeg;
        public final String structuredText;
        public final String sourcePackage;

        public Context(byte[] screenshotJpeg, String structuredText, String sourcePackage) {
            this.screenshotJpeg = screenshotJpeg;
            this.structuredText = structuredText;
            this.sourcePackage = sourcePackage;
        }

        public boolean hasVisualContext() {
            return screenshotJpeg != null && screenshotJpeg.length > 0;
        }
    }

    interface Callback {
        void onContext(Context context);
        void onUnavailable(String reason);
    }

    void capture(Callback callback);
}
