# notification-pipeline

Sends personalized email digest notifications to MY_RGD users who subscribe to watch specific genes or ontology terms.

## How it works

1. Loads configuration via Spring IoC from `properties/AppConfigure.xml`
2. Reads `last.run` timestamp from a local properties file (`-Dlocal.config`) to determine the date range; defaults to 7 days ago if no previous run
3. For each subscribed user, checks their watched objects (genes) and watched terms (ontology terms) for changes within the date range
4. Builds an HTML email with all detected changes and sends it via SMTP; also stores the message in the database
5. Updates `last.run` timestamp for the next run

## Watched object checks (genes)

- Nomenclature changes (symbol/name)
- Protein and transcript updates (Ensembl, Pfam, etc.)
- Protein-protein interactions
- PubMed reference associations
- Annotations: Disease, Pathway, Phenotype, Strain, GO (BP, MF, CC)
- External database links

## Watched term checks (ontology terms)

- New gene annotations (rat, mouse, human)
- New QTL annotations (rat, mouse, human)
- New strain annotations (rat)
- New variant annotations (rat)

## Running

    ./notificationPipeline.sh

On dev servers, runs in debug mode redirecting all emails to a test address. On production (REED), sends to actual users.

Pass `debug=user@example.com` to override the recipient for testing.
