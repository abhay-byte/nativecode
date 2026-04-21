# Data Science & AI/ML Stack

*Script: `setup_datascience_debian.sh`*

---

## Overview

Installs Python data science environment, Jupyter, TensorFlow, PyTorch, Julia, and R.

---

## Python Virtual Environment

| Component | Path |
|-----------|------|
| Virtual Environment | `/home/flux/data_env` |
| Activate | `source ~/data_env/bin/activate` |

> **Important:** Always activate the environment before using data science tools.

---

## Python Libraries

### Core Libraries
```
jupyter jupyterlab notebook
pandas numpy matplotlib seaborn
scipy scikit-learn
```

### Deep Learning
```
tensorflow torch torchvision torchaudio
keras xgboost
```

### NLP
```
nltk spacy transformers
```

### Spacy Model
```
en_core_web_sm
```

---

## Julia

| Component | Version | Path |
|-----------|---------|------|
| Julia | 1.10.2 | `/opt/julia` |
| Binary | - | `/usr/local/bin/julia` |

---

## R Language

| Component | Source |
|-----------|--------|
| R | apt (r-base) |
| R Dev | apt (r-base-dev) |

---

## IDEs

| IDE | Path |
|-----|------|
| PyCharm Community | `/opt/pycharm` |
| Spyder | apt |
| Jupyter Lab | `~/data_env/bin/jupyter-lab` |

---

## Desktop Entries

- **Jupyter Lab** - Available in Applications menu

---

## Usage

```bash
# Activate environment
source ~/data_env/bin/activate

# Start Jupyter
jupyter-lab

# Run Python with ML
python -c "import tensorflow as tf; print(tf.__version__)"

# Start Julia
julia
```

---

## Verification

```bash
source ~/data_env/bin/activate
python -c "import pandas, numpy, tensorflow, torch; print('All OK')"
julia --version
R --version
```
