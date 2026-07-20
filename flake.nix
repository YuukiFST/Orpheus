{
  description = "Orpheus Android music player dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        androidPackages = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "12.0";
          platformToolsVersion = "35.0.2";
          buildToolsVersions = [ "35.0.0" "36.0.0" ];
          platformVersions = [ "35" "36" "37" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          abiVersions = [ "x86_64" ];
        };
        fhs = pkgs.buildFHSEnv {
          name = "orpheus-android-fhs";
          targetPkgs = pkgs: with pkgs; [
            glibc
            zlib
            bzip2
            libGL
            nss
            nspr
            atk
            at-spi2-atk
            cups
            dbus
            expat
            xorg.libX11
            xorg.libXcomposite
            xorg.libXdamage
            xorg.libXext
            xorg.libXfixes
            xorg.libXrandr
            xorg.libxcb
            libdrm
            mesa
            alsa-lib
            gtk3
            cairo
            pango
            gdk-pixbuf
          ];
          profile = ''
            export ANDROID_SDK_ROOT="''${TMPDIR:-/tmp}/orpheus-android-sdk"
            export ANDROID_HOME="$ANDROID_SDK_ROOT"
            export JAVA_HOME="${pkgs.jdk21}"
            mkdir -p "$ANDROID_SDK_ROOT"
            if [ ! -f "$ANDROID_SDK_ROOT/platforms/android-37.0/android.jar" ] && [ ! -f "$ANDROID_SDK_ROOT/platforms/android-37/android.jar" ]; then
              echo "Seeding writable Android SDK from Nix store..."
              rm -rf "$ANDROID_SDK_ROOT"
              mkdir -p "$ANDROID_SDK_ROOT"
              cp -a "${androidPackages.androidsdk}/libexec/android-sdk/." "$ANDROID_SDK_ROOT/"
            fi
            export PATH="${pkgs.jdk21}/bin:${androidPackages.androidsdk}/libexec/android-sdk/emulator:${androidPackages.androidsdk}/libexec/android-sdk/platform-tools:$PATH"
          '';
        };
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk21
            gradle
            androidPackages.androidsdk
          ];
          shellHook = ''
            echo "Orpheus dev shell (FHS wrapper for Gradle/AAPT2 on NixOS)"
            echo "  Run builds via: nix develop --impure -c ./scripts/build.sh"
            echo "  Or enter FHS env: ${fhs}/bin/orpheus-android-fhs"
            export JAVA_HOME="${pkgs.jdk21}"
          '';
        };

        packages.default = fhs;

        apps.assemble-debug = flake-utils.lib.mkApp {
          drv = pkgs.writeShellScriptBin "assemble-debug" ''
            export ANDROID_SDK_ROOT="''${TMPDIR:-/tmp}/orpheus-android-sdk"
            export ANDROID_HOME="$ANDROID_SDK_ROOT"
            mkdir -p "$ANDROID_SDK_ROOT"
            if [ ! -d "$ANDROID_SDK_ROOT/platforms" ]; then
              cp -a "${androidPackages.androidsdk}/libexec/android-sdk/." "$ANDROID_SDK_ROOT/"
            fi
            echo "sdk.dir=$ANDROID_SDK_ROOT" > "${self}/local.properties"
            cd "${self}"
            ${fhs}/bin/orpheus-android-fhs ./gradlew assembleDebug -Porcheus.disableReleaseSigning=true --no-daemon
          '';
        };
      });
}
