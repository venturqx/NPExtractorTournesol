# NP Extractor Tournesol

This is a community fork of NewPipe Extractor with Tournesol-related changes.
It is not affiliated with or endorsed by the NewPipe project.

## Usage

This library is published via JitPack under:

```
com.github.venturqx:NPExtractorTournesol
```

## Local development

Use a composite build to point NewPipe to this checkout:

```groovy
includeBuild('../NPExtractorTournesol') {
    dependencySubstitution {
        substitute module('com.github.venturqx:NPExtractorTournesol') with project(':extractor')
    }
}
```

## License

Licensed under GPL-3.0-or-later. See `LICENSE`.
