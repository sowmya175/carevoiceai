# Plan generation task contract

Version: `carevoice-plan-task-v1`

Dataset version: `carevoice-plan-generation-v1`

The same text is used for the Vertex tuning export, base plan inference, and tuned plan inference. A change to this text needs a new task-contract version. A change to the field catalog or example targets needs a new dataset version. A tuned model resource is a third version, stored on each proposal as developer metadata and selected with `CAREVOICE_PLAN_TUNED_MODEL`.

The contract asks for one `PlanGenerationResult` JSON object. It does not ask for a diagnosis, a prescription, a medication change, a threshold, or a chain of thought.
