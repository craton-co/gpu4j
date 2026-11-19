# Publishing `gpu4j` to Maven Central (`0.1.0`)

Step-by-step instructions for [Craton Software Company](https://craton.com.ar) maintainers to publish **`io.github.craton-co:gpu4j`** and **`io.github.craton-co:gpu4j-core`** to Sonatype's [Central Publisher Portal](https://central.sonatype.com).

| Item | Value |
|------|-------|
| GroupId | `io.github.craton-co` |
| Artifacts | `gpu4j` (parent POM), `gpu4j-core` (JAR + `-sources.jar` + `-javadoc.jar`) |
| Current Version | `0.1.0` |
| License | Apache License 2.0 (bundled in `META-INF/LICENSE` and `META-INF/NOTICE`) |
| Release Profile | `-Prelease` (sources, Javadoc, GPG `.asc` signatures, Central Portal upload) |
| CI Workflow | [`.github/workflows/release.yml`](../.github/workflows/release.yml) |
| Portal Publish Mode | Manual (`autoPublish` is `false` in `pom.xml`) |

---

## 1. One-Time Setup

### 1.1 Verify the `io.github.craton-co` Namespace
1. Sign in at [central.sonatype.com](https://central.sonatype.com) using the GitHub account/org that owns `github.com/craton-co`.
2. Open **Profile -> View Namespaces** and confirm `io.github.craton-co` is **Verified**.

### 1.2 Generate and Publish a GPG Signing Key
Maven Central requires every uploaded file to carry a `.asc` signature from a key published on a public OpenPGP keyserver:

```bash
# 1. Generate a long-lived signing key
gpg --full-generate-key

# 2. Find the long key ID (on the "sec" line)
gpg --list-secret-keys --keyid-format=long

# 3. Publish the public key to keyservers used by Sonatype Central
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# 4. (For GitHub Actions) Export the ASCII-armored private key
gpg --armor --export-secret-keys <KEY_ID>
```

### 1.3 Generate a Central Portal User Token
1. Open [central.sonatype.com/usertoken](https://central.sonatype.com/usertoken).
2. Click **Generate User Token** and save the generated **username** and **password** (shown once).

---

## 2. Pre-Release Verification (Local Docker CI)

Before tagging or deploying, run the full local CI matrix across JDK 17, 21, and 25 in Docker:

```powershell
# Windows (PowerShell)
./scripts/local-ci.ps1
```

```bash
# Linux / macOS
./scripts/local-ci.sh
```

Verify that:
- `pom.xml` `<version>` and `gpu4j-core/pom.xml` `<parent><version>` match the target release (e.g. `0.1.0`, no `-SNAPSHOT`).
- `<scm><tag>` in `pom.xml` matches the Git tag (`v0.1.0`).
- `CHANGELOG.md` has a dated section for `## [0.1.0]`.

---

## 3. Option A: Automated Release via GitHub Actions (Recommended)

### 3.1 Configure Repository Secrets
In GitHub **Settings -> Secrets and variables -> Actions** for `craton-co/gpu4j`, add:

| Secret | Value |
|--------|-------|
| `CENTRAL_USERNAME` | Central Portal user token username |
| `CENTRAL_PASSWORD` | Central Portal user token password |
| `MAVEN_GPG_PRIVATE_KEY` | Output of `gpg --armor --export-secret-keys <KEY_ID>` |
| `MAVEN_GPG_PASSPHRASE` | Passphrase protecting `<KEY_ID>` |

### 3.2 Tag and Push
```bash
git tag -a v0.1.0 -m "Release 0.1.0"
git push origin v0.1.0
```

GitHub Actions (`.github/workflows/release.yml`) will verify the tag matches `pom.xml`, run `mvn -B -ntp -Prelease deploy`, and attach the signed artifacts to a GitHub Release.

### 3.3 Finalize in Sonatype Central Portal
Because `<autoPublish>false</autoPublish>` is set in `pom.xml`:
1. Open [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments).
2. Confirm the deployment passes validation, then click **Publish**.

---

## 4. Option B: Manual Publish from a Workstation

### 4.1 Configure `~/.m2/settings.xml` (do not commit)
```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.MAVEN_USERNAME}</username>
      <password>${env.MAVEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

### 4.2 Sign, Bundle, and Upload
```bash
export MAVEN_USERNAME="<portal-token-username>"
export MAVEN_PASSWORD="<portal-token-password>"
export MAVEN_GPG_PASSPHRASE="<gpg-passphrase>"

mvn -B -ntp -Prelease clean deploy
```

Then open [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments) and click **Publish**.

---

## 5. Post-Release Verification

1. Confirm `io.github.craton-co:gpu4j-core:0.1.0` appears on [central.sonatype.com/artifact/io.github.craton-co/gpu4j-core](https://central.sonatype.com/artifact/io.github.craton-co/gpu4j-core) (sync takes ~10–30 minutes).
2. Test consumption from a clean project:
   ```xml
   <dependency>
       <groupId>io.github.craton-co</groupId>
       <artifactId>gpu4j-core</artifactId>
       <version>0.1.0</version>
   </dependency>
   ```
3. To enable zero-click publishing from trusted CI later, change `<autoPublish>false</autoPublish>` to `<autoPublish>true</autoPublish>` in `pom.xml`.
