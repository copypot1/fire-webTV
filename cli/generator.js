#!/usr/bin/env node

/**
 * FireWeb TV - Standalone Web App Generator
 * 
 * Allows creating a dedicated standalone Android TV app for any website.
 * The generated app launches directly into the target website with:
 * - Built-in ad & popup blocking
 * - Fire TV D-pad remote virtual mouse navigation
 * - Fullscreen HTML5 video support
 * - Home screen TV banner and Leanback launcher
 * 
 * Usage:
 *   node generator.js --name "HiAnime TV" --url "https://hianime.to" --adblock true --cursor true
 */

const fs = require('fs');
const path = require('path');

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    name: 'My Web App',
    url: '',
    adblock: true,
    cursor: true,
    userAgent: '',
    outDir: null
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--name' && args[i + 1]) options.name = args[++i];
    else if (arg === '--url' && args[i + 1]) options.url = args[++i];
    else if (arg === '--adblock') options.adblock = args[++i] !== 'false';
    else if (arg === '--cursor') options.cursor = args[++i] !== 'false';
    else if (arg === '--ua' && args[i + 1]) options.userAgent = args[++i];
    else if (arg === '--out' && args[i + 1]) options.outDir = args[++i];
    else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    }
  }

  return options;
}

function printHelp() {
  console.log(`
FireWeb TV - Standalone Web App Generator

Options:
  --name       Name of the app (e.g. "HiAnime TV", "Sportsurge TV")
  --url        Full website URL (e.g. "https://hianime.to")
  --adblock    Enable ad and popup blocker (true/false, default: true)
  --cursor     Enable virtual mouse cursor by default (true/false, default: true)
  --ua         Custom User-Agent string (optional)
  --out        Output directory (default: ./dist/<slug>)
  --help       Show this help message

Example:
  node generator.js --name "Anime Stream" --url "https://hianime.to" --adblock true
`);
}

function slugify(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
}

function copyRecursiveSync(src, dest) {
  const exists = fs.existsSync(src);
  const stats = exists && fs.statSync(src);
  const isDirectory = exists && stats.isDirectory();
  if (isDirectory) {
    if (!fs.existsSync(dest)) fs.mkdirSync(dest, { recursive: true });
    fs.readdirSync(src).forEach((childItemName) => {
      copyRecursiveSync(path.join(src, childItemName), path.join(dest, childItemName));
    });
  } else {
    fs.copyFileSync(src, dest);
  }
}

async function main() {
  const options = parseArgs();

  if (!options.url) {
    console.error('❌ Error: --url is required.\nRun "node generator.js --help" for usage.');
    process.exit(1);
  }

  if (!options.url.startsWith('http://') && !options.url.startsWith('https://')) {
    options.url = 'https://' + options.url;
  }

  const slug = slugify(options.name);
  const pkgName = `com.fireweb.app.${slug}`;
  const templateDir = path.resolve(__dirname, '../android');
  const targetDir = options.outDir ? path.resolve(options.outDir) : path.resolve(__dirname, `../dist/${slug}`);

  console.log(`\n🚀 Generating Standalone Fire TV Web App...`);
  console.log(`   • Name:      ${options.name}`);
  console.log(`   • URL:       ${options.url}`);
  console.log(`   • AdBlock:   ${options.adblock ? 'ENABLED' : 'DISABLED'}`);
  console.log(`   • Cursor:    ${options.cursor ? 'ENABLED' : 'DISABLED'}`);
  console.log(`   • Package:   ${pkgName}`);
  console.log(`   • Output:    ${targetDir}\n`);

  // 1. Copy android project template
  if (fs.existsSync(targetDir)) {
    fs.rmSync(targetDir, { recursive: true, force: true });
  }
  fs.mkdirSync(targetDir, { recursive: true });
  copyRecursiveSync(templateDir, targetDir);

  // 2. Modify strings.xml
  const stringsPath = path.join(targetDir, 'app/src/main/res/values/strings.xml');
  const customStrings = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${options.name}</string>
</resources>
`;
  fs.writeFileSync(stringsPath, customStrings, 'utf8');

  // 3. Modify build.gradle
  const buildGradlePath = path.join(targetDir, 'app/build.gradle');
  let buildGradle = fs.readFileSync(buildGradlePath, 'utf8');
  buildGradle = buildGradle.replace('applicationId "com.fireweb.tv"', `applicationId "${pkgName}"`);
  fs.writeFileSync(buildGradlePath, buildGradle, 'utf8');

  // 4. Update AndroidManifest.xml so WebActivity is the direct launcher
  const manifestPath = path.join(targetDir, 'app/src/main/AndroidManifest.xml');
  const customManifest = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${pkgName}">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/tv_banner"
        android:banner="@drawable/tv_banner"
        android:label="@string/app_name"
        android:theme="@style/AppTheme"
        android:hardwareAccelerated="true"
        android:usesCleartextTraffic="true">

        <!-- Standalone Direct Launcher Activity -->
        <activity
            android:name="com.fireweb.tv.WebActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|screenLayout|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
`;
  fs.writeFileSync(manifestPath, customManifest, 'utf8');

  // 5. Update WebActivity default extras so it launches right into the configured site
  const webActivityPath = path.join(targetDir, 'app/src/main/java/com/fireweb/tv/WebActivity.java');
  let webActivityContent = fs.readFileSync(webActivityPath, 'utf8');
  
  // Replace targetUrl default
  webActivityContent = webActivityContent.replace(
    'targetUrl = "https://duckduckgo.com";',
    `targetUrl = "${options.url}";`
  );
  webActivityContent = webActivityContent.replace(
    'isAdBlockEnabled = getIntent().getBooleanExtra(EXTRA_ADBLOCK, true);',
    `isAdBlockEnabled = getIntent().getBooleanExtra(EXTRA_ADBLOCK, ${options.adblock});`
  );
  webActivityContent = webActivityContent.replace(
    'boolean defaultCursor = getIntent().getBooleanExtra(EXTRA_CURSOR_DEFAULT, true);',
    `boolean defaultCursor = getIntent().getBooleanExtra(EXTRA_CURSOR_DEFAULT, ${options.cursor});`
  );
  if (options.userAgent) {
    webActivityContent = webActivityContent.replace(
      'DEFAULT_DESKTOP_UA =',
      `DEFAULT_DESKTOP_UA = "${options.userAgent}"; //`
    );
  }
  fs.writeFileSync(webActivityPath, webActivityContent, 'utf8');

  // 6. Attempt automatic compilation to .apk if tools are available
  console.log(`\n⚙️  Attempting to compile .apk package...`);
  let apkBuilt = false;

  // Auto-detect Java in home directory or standard locations
  const homeDir = process.env.HOME || '';
  const possibleJavaDirs = [
    path.join(homeDir, 'openJdk-25.jdk/Contents/Home'),
    '/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home'
  ];
  for (const dir of possibleJavaDirs) {
    if (fs.existsSync(dir) && !process.env.JAVA_HOME) {
      process.env.JAVA_HOME = dir;
      process.env.PATH = `${path.join(dir, 'bin')}:${process.env.PATH}`;
      break;
    }
  }

  // Auto-detect Android CLI
  const possibleAndroidPaths = [
    path.join(homeDir, '.android-cli/bin'),
    path.join(homeDir, 'bin')
  ];
  for (const p of possibleAndroidPaths) {
    if (fs.existsSync(p) && !process.env.PATH.includes(p)) {
      process.env.PATH = `${p}:${process.env.PATH}`;
    }
  }

  try {
    const { execSync } = require('child_process');
    console.log(`   Running build in ${targetDir}...`);
    execSync(`cd "${targetDir}" && chmod +x gradlew && ./gradlew assembleDebug`, { stdio: 'inherit' });

    const generatedApk = path.join(targetDir, 'app/build/outputs/apk/debug/app-debug.apk');
    const finalApk = path.join(path.dirname(targetDir), `${slug}.apk`);
    if (fs.existsSync(generatedApk)) {
      fs.copyFileSync(generatedApk, finalApk);
      console.log(`\n🎉 SUCCESS! Your APK is ready:`);
      console.log(`   👉 ${finalApk}\n`);
      apkBuilt = true;
    }
  } catch (err) {
    console.log(`   ℹ️  Build step message: ${err.message || 'Waiting for build tools'}`);
  }

  if (!apkBuilt) {
    console.log(`\n⚠️  The project code is ready at '${targetDir}', but the .apk has not finished compiling yet.`);
    console.log(`\nIf the android tool just finished installing, make sure to reload your terminal session:`);
    console.log(`   source ~/.zshrc`);
    console.log(`And make sure you have the platform installed:`);
    console.log(`   android sdk install platforms/android-34`);
    console.log(`\nThen re-run the generator!`);
  }
}

main().catch(err => {
  console.error('Error:', err);
  process.exit(1);
});
