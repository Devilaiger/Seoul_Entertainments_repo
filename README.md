# Seoul Entertainments Extensions Repository

Welcome to the **Seoul Entertainments Extensions Repository** for CloudStream! This repository hosts premium in-house extensions customized and optimized for Korean dramas, movies, and high-quality streaming providers.

---

## 🕹 Features & Tools
* **Language Support**: English, Korean, and multilingual sources.
* **Auto-updates**: All extensions are built and updated automatically at runtime.
* **Direct Install**: One-click install compatible with standard CloudStream and custom clients.

---

## 🚀 Installation

### Method 1: One-Click Add (Recommended)
If you are reading this on the device where CloudStream is installed, tap the button below to add the repository automatically:

<a href="cloudstreamrepo://raw.githubusercontent.com/Devilaiger/Seoul_Entertainments_repo/builds/repo.json" target="_blank">
  <img src="https://img.shields.io/badge/Add%20to-CloudStream-blue?style=for-the-badge&logo=android&logoColor=white" alt="Add to CloudStream" />
</a>

---

### Method 2: Manual Add
If the one-click method does not work:
1. Open **CloudStream**.
2. Go to **Settings** -> **Extensions**.
3. Tap **Add Repository**.
4. Enter the repository name: `Seoul Entertainments`
5. Paste the following URL:
   ```text
   https://raw.githubusercontent.com/Devilaiger/Seoul_Entertainments_repo/builds/repo.json
   ```
6. Tap **Add Repository** and select the extensions you want to install.

---

## 📦 Available Extensions

| Name | Language | Description |
| --- | --- | --- |
| **KatMovieHD** | English / Multi | Premium high-quality movies and TV Series. |
| **MPlayer** | Hindi / Multi | Indian Movies/Series/Kdrama(Hindi Dubbed). |
| **MovieLinkBD** | Bengali | MovieLinkBD Provider. |

---

## 🛠 For Developers (Build & Deploy)

To build the repository locally:
1. Initialize the gradle build system:
   ```bash
   ./gradlew make makePluginsJson
   ```
2. The built `.cs3` files are saved in the `build/` folder, and metadata is generated in `build/plugins.json`.
3. Pushing commits to the `master` or `main` branches will trigger the GitHub Actions runner to compile and force-push builds to the `builds` branch automatically.
