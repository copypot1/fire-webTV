/**
 * FireWeb TV Automated Verification Suite
 * Tests the Ad-Blocker domain matcher, cosmetic CSS injection, and project generator.
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

console.log('🧪 Starting FireWeb TV Test Suite...\n');

// 1. Test Blocklist Assets
console.log('Test 1: Verifying blocklist assets...');
const domainsPath = path.resolve(__dirname, '../android/app/src/main/assets/adblock_domains.txt');
const cssPath = path.resolve(__dirname, '../android/app/src/main/assets/adblock_cosmetic.css');
const defaultAppsPath = path.resolve(__dirname, '../android/app/src/main/assets/default_apps.json');

if (!fs.existsSync(domainsPath)) throw new Error('adblock_domains.txt missing!');
if (!fs.existsSync(cssPath)) throw new Error('adblock_cosmetic.css missing!');
if (!fs.existsSync(defaultAppsPath)) throw new Error('default_apps.json missing!');

const domainLines = fs.readFileSync(domainsPath, 'utf8')
  .split('\n')
  .map(l => l.trim().toLowerCase())
  .filter(l => l && !l.startsWith('#'));

console.log(`   ✓ Loaded ${domainLines.length} ad/tracker domains.`);

// 2. Test Domain Matching Logic (replicating AdBlockEngine.isAd)
console.log('Test 2: Testing domain matcher logic...');
const domainSet = new Set(domainLines);

function isAd(urlStr) {
  try {
    const parsed = new URL(urlStr);
    let host = parsed.hostname.toLowerCase();

    if (domainSet.has(host)) return true;

    let dotIndex = host.indexOf('.');
    while (dotIndex > 0 && dotIndex < host.length - 1) {
      const parent = host.substring(dotIndex + 1);
      if (domainSet.has(parent)) return true;
      dotIndex = host.indexOf('.', dotIndex + 1);
    }

    const full = urlStr.toLowerCase();
    if (full.includes('/ads.js') || full.includes('/pagead/') ||
        full.includes('googlesyndication') || full.includes('/ad-server/') ||
        full.includes('/popunder') || full.includes('adnxs.com')) {
      return true;
    }

    return false;
  } catch (e) {
    return false;
  }
}

const testCases = [
  { url: 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js', expected: true, desc: 'Google AdSense script' },
  { url: 'https://adservice.google.com/adsid/integrator.js', expected: true, desc: 'Google Ad service' },
  { url: 'https://subdomain.doubleclick.net/pixel', expected: true, desc: 'DoubleClick subdomain' },
  { url: 'https://c.popcash.net/pop.js', expected: true, desc: 'PopCash popup script' },
  { url: 'https://s.amazon-adsystem.com/iu3', expected: true, desc: 'Amazon ad system' },
  { url: 'https://media.outbrain.com/widget', expected: true, desc: 'Outbrain widget' },
  { url: 'https://wikipedia.org/wiki/Main_Page', expected: false, desc: 'Legitimate Wikipedia site' },
  { url: 'https://hianime.to/watch/one-piece', expected: false, desc: 'Target streaming page host' },
  { url: 'https://duckduckgo.com/?q=tv', expected: false, desc: 'DuckDuckGo search' }
];

let passedCases = 0;
for (const tc of testCases) {
  const result = isAd(tc.url);
  if (result === tc.expected) {
    console.log(`   ✓ [PASS] ${tc.desc} (${result ? 'BLOCKED' : 'ALLOWED'})`);
    passedCases++;
  } else {
    console.error(`   ✗ [FAIL] ${tc.desc} expected ${tc.expected}, got ${result}`);
  }
}

if (passedCases !== testCases.length) {
  throw new Error('Some ad-blocker domain tests failed!');
}

// 3. Test Default Apps JSON Structure
console.log('\nTest 3: Testing default apps configuration...');
const defaultApps = JSON.parse(fs.readFileSync(defaultAppsPath, 'utf8'));
if (!Array.isArray(defaultApps) || defaultApps.length === 0) {
  throw new Error('default_apps.json is empty or not an array!');
}
for (const app of defaultApps) {
  if (!app.name || !app.url || app.adBlockEnabled === undefined) {
    throw new Error(`Invalid app entry: ${JSON.stringify(app)}`);
  }
}
console.log(`   ✓ All ${defaultApps.length} default web apps verified valid.`);

// 4. Test Standalone App Generator
console.log('\nTest 4: Testing Standalone App Generator CLI...');
const testOutDir = path.resolve(__dirname, '../dist/test_anime_app');
execSync(`node "${path.join(__dirname, 'generator.js')}" --name "Anime Test App" --url "https://hianime.to" --out "${testOutDir}"`);

const testManifest = fs.readFileSync(path.join(testOutDir, 'app/src/main/AndroidManifest.xml'), 'utf8');
const testStrings = fs.readFileSync(path.join(testOutDir, 'app/src/main/res/values/strings.xml'), 'utf8');
const testWebActivity = fs.readFileSync(path.join(testOutDir, 'app/src/main/java/com/fireweb/tv/WebActivity.java'), 'utf8');

if (!testManifest.includes('package="com.fireweb.app.anime_test_app"')) {
  throw new Error('Generated manifest does not contain correct package name!');
}
if (!testStrings.includes('Anime Test App')) {
  throw new Error('Generated strings.xml does not contain correct app name!');
}
if (!testWebActivity.includes('https://hianime.to')) {
  throw new Error('Generated WebActivity does not contain target URL!');
}

console.log('   ✓ Generator successfully created customized standalone Android TV project!');

// Cleanup test output
fs.rmSync(testOutDir, { recursive: true, force: true });
console.log('   ✓ Cleaned up test artifacts.');

console.log('\n🎉 ALL VERIFICATION TESTS PASSED SUCCESSFULLY!\n');
