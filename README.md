# CropScope


# 🌾 CropScope

**CropScope: A Metadata-Driven, Multi-Scale, Cloud-Integrated Software Suite for Reproducible Computer Vision Datasets**

CropScope is an open-source, Java-based software suite designed to automate and standardize the **generation, transformation, and validation of large-scale image datasets** for artificial intelligence (AI) and computer vision research across domains such as **medicine, agriculture, and autonomous driving**.

It bridges the gap between raw imagery and analysis-ready datasets by integrating:
- **Structured image cropping and scaling**
- **Metadata-driven dataset generation**
- **Cloud-native data management (S3-compatible storage)**
- **Multi-domain format support** (DICOM, NIfTI, RGB, and video)
- **Provenance tracking and reproducibility**
- **Quality control tools for deduplication and visual diversity**

All code and documentation comply with FAIR data principles — **Findable, Accessible, Interoperable, and Reusable** — and are released under the **Apache License 2.0**.

---

## 🚀 Key Features

- **🧩 Metadata-first architecture:**  
  Every crop, transformation, and export operation is logged in deterministic JSON manifests for full traceability and reproducibility.

- **🔬 Multi-domain image support:**  
  Works seamlessly with medical (DICOM, NIfTI), agricultural, and autonomous-driving datasets.

- **📦 Cloud integration via S3Service:**  
  CropScope integrates a custom-built `S3Service` module — a secure, efficient, and user-friendly **Cloud Storage Integration Layer** that provides native connectivity to **S3-compatible object storage systems** such as Amazon S3, Backblaze B2, and MinIO.  
  The service acts as a lightweight SDK wrapper around the AWS SDK for Java, abstracting low-level complexity while enabling high-level operations essential for AI and data-intensive workflows.

  **Key capabilities include:**
  - ✅ Unified interface through `StorageService` for cross-backend integration  
  - ✅ Secure authentication via encrypted access and secret keys in `ConnectionProfile`  
  - ✅ Full bucket and object management (create, list, upload, download, rename, delete)  
  - ✅ Recursive directory uploads preserving local hierarchy  
  - ✅ In-memory streaming using `uploadBytes()` and `downloadBytes()` for fast, diskless I/O  
  - ✅ Progress tracking via callback mechanisms  
  - ✅ Thread-safe design for GUI and batch environments using `SwingWorker`

  **Technical overview:**
  - Built atop **AmazonS3Client** (AWS SDK v1)
  - Implements robust exception handling for network and credential errors  
  - Extensible through the `StorageService` interface for future multi-cloud compatibility  
  - Manages connection lifecycle (`connect()` / `disconnect()`) and endpoint configuration  
  - Core class: `com.cropscope.cloudstorage.service.S3Service`

  **Use cases:**
  - AI/ML data pipelines (upload or stream large image batches)  
  - Synthetic data generation (direct in-memory uploads)  
  - Distributed model training (shared cloud buckets)  
  - Cross-project dataset sharing and metadata synchronization  

- **🧠 Multi-scale crop generation:**  
  Automatically produces aligned image pyramids (e.g., 512×512, 256×256, 128×128) from a single interaction, compatible with modern feature pyramid networks (FPNs) and multi-resolution deep learning models.

- **⚙️ Batch and GUI workflows:**  
  Provides a full-featured **Graphical User Interface (GUI)** for domain experts and a **command-line batch processor** for automated, large-scale operations.

- **🧪 Validation and reproducibility:**  
  Includes the *Image Verify Tool* for near-duplicate detection using **SSIM**, **PSNR**, and **dHash**, achieving **98.7 % precision in deduplication** and reducing manual curation time by up to **70 %**.

- **📈 Proven dataset quality:**  
  CropScope has been validated across >110,000 curated images, covering medical, agricultural, and autonomous driving imagery.

---

## 🧰 System Requirements

| Component | Recommended Specification |
|------------|----------------------------|
| **OS** | Linux (RHEL 9+, Ubuntu 22.04+), macOS, or Windows 10+ |
| **Java Runtime** | Java 1.8 or newer |
| **Memory** | ≥ 8 GB (16 GB recommended) |
| **GPU (optional)** | For accelerated batch export and visualization |
| **Cloud Connectivity** | AWS S3-compatible endpoint (e.g., MinIO, Backblaze B2) |

---

## ⚙️ Installation

### Clone the repository
```bash
git clone https://github.com/cropscope/cropscope.git
cd cropscope

Build from source
mvn clean package

Launch GUI
java -jar cropscope-gui.jar

Launch Batch Processor
java -jar cropscope-batch.jar --config your_config.json

📦 Example Usage (S3Service)
ConnectionProfile profile = new ConnectionProfile(
    "My AWS", "ACCESS_KEY", "SECRET_KEY",
    "https://s3.amazonaws.com", "us-east-1", "AWS_S3"
);

StorageService service = new S3Service(profile);

if (service.connect()) {
    // Upload a file
    service.uploadFile("my-bucket", new File("/data/image.png"), "raw/images/img001.png");

    // Download into memory for immediate use
    byte[] imageData = service.downloadBytes("my-bucket", "raw/images/img001.png");
}

🧪 Dataset Structure

CropScope produces datasets in a standardized hierarchy for both local and cloud environments:
Local:
{SINK_FOLDER}/{SUBFOLDER}/{WIDTH}x{HEIGHT}/{FILENAME}.png

Cloud (S3-compatible):
{BUCKET_NAME}/{PREFIX}/{WIDTH}x{HEIGHT}/{FILENAME}.png

Associated metadata:
Crop_Metadata_{TIMESTAMP}/
 ├── crop_metadata_{TIMESTAMP}.json
 └── coco_instances_{TIMESTAMP}.json

This structure guarantees consistency across platforms and facilitates ingestion by downstream machine learning pipelines.

📊 Validation and Quality Control
| Metric    | Purpose               | Threshold Example   |
| --------- | --------------------- | ------------------- |
| **dHash** | Perceptual similarity | ≤ 5 (Medium preset) |
| **PSNR**  | Signal fidelity (dB)  | ≥ 40 dB             |
| **SSIM**  | Structural similarity | ≥ 0.98              |

These quality controls ensure that datasets are diverse, non-redundant, and optimized for robust AI model training.

💾 Data Availability

A representative dataset processed using CropScope is publicly available on Zenodo:
👉 https://doi.org/10.5281/zenodo.xxxxxxx

📚 Citation

If you use CropScope in your research, please cite:

AAG et al.
CropScope: A Metadata-Driven, Multi-Scale, Cloud-Integrated Software Suite for Reproducible Computer Vision Datasets.
Scientific Data (2025). DOI: 10.1038/sdata.xxxxx

👩‍💻 Contributing

Contributions are welcome!
Please fork the repository and submit pull requests with descriptive commit messages and working examples.
See CONTRIBUTING.md for code style and testing guidelines.

🧩 License

CropScope is distributed under the Apache License 2.0.
See the LICENSE

🧠 Acknowledgements

CropScope was developed through interdisciplinary collaborations in precision agriculture, medical imaging, and AI reproducibility research.
We thank the open-source community for validation feedback and contributions.







