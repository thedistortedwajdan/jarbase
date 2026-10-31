# Releasing jarbase to Maven Central

A release is **permanent**: Maven Central never deletes or changes a published version. Do a dry run first (step 5) and read the bundle in the Portal before pressing "Publish".

## One-time setup

### 1. A Central Portal account and your namespace

1. Go to <https://central.sonatype.com> and sign in **with GitHub**.
2. Open **Namespaces**. `io.github.thedistortedwajdan` should be listed and verified. If it shows as unverified, the Portal displays a verification key: create a **temporary public repository** on your GitHub account named exactly that key, click **Verify**, then delete the repository.

### 2. A publishing token

1. In the Portal, open your account menu, then **View Account**, then **Generate User Token**.
2. Put the token in `~/.m2/settings.xml` (on Windows: `C:\Users\<you>\.m2\settings.xml`). The server id must be `central`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME_FROM_THE_PORTAL</username>
         <password>TOKEN_PASSWORD_FROM_THE_PORTAL</password>
       </server>
     </servers>
   </settings>
   ```

   Never commit this file or paste the token anywhere else.

### 3. A GPG key

Central requires every file to be signed, and your public key must be on a public key server.

1. Install GnuPG (on Windows: [Gpg4win](https://www.gpg4win.org/)).
2. Create a key: `gpg --full-generate-key` (RSA 4096, a passphrase you will remember).
3. Find its id: `gpg --list-secret-keys --keyid-format long`. It is the long hex id after `sec   rsa4096/`.
4. Publish the public key: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`.
5. Keep a backup of the secret key (`gpg --export-secret-keys --armor <KEY_ID>`) somewhere private.

## Every release

### 4. Pick the version

```bash
./mvnw versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
git commit -am "Release 0.1.0"
```

Use the same version in the `README` snippet.

### 5. Dry run (builds, signs, uploads nothing)

```bash
./mvnw -Prelease verify
```

You will be asked for the GPG passphrase (or set `MAVEN_GPG_PASSPHRASE`). Check that each module has `-sources.jar`, `-javadoc.jar` and `.asc` signature files in its `target/` folder.

### 6. Upload

```bash
./mvnw -Prelease deploy
```

This uploads one bundle with all modules to the Portal. It is **not** published yet: `autoPublish` is `false`.

### 7. Publish

1. Open <https://central.sonatype.com/publishing/deployments>.
2. Wait for the deployment to show **Validated**. Fix anything it reports and upload again.
3. Press **Publish**. The artifacts appear on Maven Central after a few minutes.

### 8. Tag and move on

```bash
git tag v0.1.0
git push origin main --tags
./mvnw versions:set -DnewVersion=0.1.1-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Start 0.1.1-SNAPSHOT"
git push
```

## Check the result

In an empty folder, a project that uses the plugin from Central (not your local cache) should resolve it:

```bash
mvn -U io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:0.1.0:install
```
