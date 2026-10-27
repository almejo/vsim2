# Para desarrolladores

Parte de las [funciones de VSim 2](../funciones.md).

- Las reglas del código están en [conventions.md](../../conventions.md).
- Los archivos de los ejemplos se generan con `CombinationalExamples`, `SequentialExamples` y `BusAndAluExamples` (pruebas de `core`, listados por `BundledCircuits`): después de cambiar uno, `VSIM_WRITE_BUNDLED=1 ./gradlew :core:test --tests '*BundledCircuitsTest.writeTheResources'` los reescribe; las pruebas comprueban que coinciden con lo generado, que ningún cable pasa sobre un pin, que ningún texto tapa nada y que cada ejemplo funciona.
