# Kodio Documentation

This module contains the Kodio library documentation built with [JetBrains Writerside](https://www.jetbrains.com/writerside/).

## Structure

```
kodio-docs/
├── writerside.cfg        # Main Writerside configuration
├── k.tree                # Table of contents / navigation
├── v.list                # Variables (versions, etc.)
├── c.list                # Categories
├── redirection-rules.xml # URL redirects
├── cfg/
│   └── buildprofiles.xml # Build configuration
├── topics/               # Documentation content
│   ├── Getting-Started.md
│   ├── Quick-Start.md
│   ├── Installation.md
│   ├── Platform-Setup.md
│   ├── core/            # Core API docs
│   ├── compose/         # Compose integration docs
│   └── advanced/        # Advanced topics
└── images/              # Documentation images
```

## Local Development

### Prerequisites

1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/) (any edition)
2. Install the [Writerside plugin](https://plugins.jetbrains.com/plugin/20158-writerside)

### Working with Documentation

1. Open the Kodio project in IntelliJ IDEA
2. The Writerside tool window should appear automatically
3. Edit topics in `topics/` directory
4. Use the live preview to see changes

### Build Locally

You can build the documentation locally using Docker:

```bash
docker run --rm -v $(pwd)/kodio-docs:/docs jetbrains/writerside-builder:243.22562 \
    --product k \
    --output-dir /docs/build
```

## Deployment

Documentation is automatically deployed to GitHub Pages when changes are pushed to `main`.

The workflow is defined in `.github/workflows/docs.yml`.

### Manual Deployment

1. Push changes to the `main` branch
2. The GitHub Action will build and deploy automatically
3. View at your GitHub Pages URL

## Adding New Topics

<procedure>
1. Create a new `.md` file in the appropriate `topics/` subdirectory
2. Add the topic to `k.tree` in the correct location
3. Use Writerside semantic markup for rich content
</procedure>

### Topic Template

```markdown
# Topic Title

<primary-label ref="core"/>
<secondary-label ref="0.0.6"/>

<show-structure for="chapter,procedure" depth="2"/>

Brief introduction to the topic.

## Section

Content here...

<tip>
Helpful advice for users.
</tip>

<note>
Important information users should know.
</note>

<warning>
Critical information about potential issues.
</warning>

## Properties {collapsible="true" default-state="expanded"}

<deflist collapsible="true">
<def title="propertyName: Type" default-state="expanded">Description of the property.</def>
</deflist>

## See Also

<seealso>
    <category ref="core-api">
        <a href="Related-Topic.md">Related Topic</a>
    </category>
</seealso>
```

### Semantic Markup Elements

Use these inline elements for proper semantic meaning:

- `<code>` - For code identifiers like `Kodio.record()`
- `<path>` - For file paths like `AndroidManifest.xml`
- `<ui-path>` - For UI navigation like `File | Settings`
- `<control>` - For UI elements like buttons
- `<emphasis>` - For emphasized text

### Structural Elements

- `<procedure>` with `<step>` - For step-by-step instructions
- `<deflist>` with `<def>` - For definition lists (API reference)
- `{collapsible="true"}` - Make sections collapsible
- `<show-structure>` - Control topic navigation sidebar

## Variables

Common variables are defined in `v.list`:

- `%kodio-version%` - Current library version
- `%kotlin-version%` - Kotlin version
- `%maven-group%` - Maven group ID
- `%github-repo%` - GitHub repository URL

Use them in topics: `The current version is %kodio-version%.`

## Versioning

When releasing a new version:

1. Update `%kodio-version%` in `v.list`
2. Update `cfg/buildprofiles.xml` if needed
3. Add migration notes to `topics/Migration.md`

## Resources

- [Writerside Documentation](https://www.jetbrains.com/help/writerside/)
- [Semantic Markup Reference](https://www.jetbrains.com/help/writerside/semantic-markup-reference.html)
- [GitHub Actions for Writerside](https://www.jetbrains.com/help/writerside/deploy-docs-to-github-pages.html)
