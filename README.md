# TRABAJO PRÁCTICO DE IMPLEMENTACIÓN: ESTEGANOGRAFÍA

Implementación de `stegobmp` con Java 21, Maven y picocli.
Permite ocultar y extraer archivos con LSB1 y LSB4 sin cifrado en BMP V3 de 24 bits
sin compresión. LSBI, cifrado y estegoanálisis están pendientes.

## Estructura

En `stegobmp/src/main/java/stegobmp/`: `Main` inicia el programa, `cli/` valida
argumentos y `StegoService` coordina las operaciones. `bmp/` lee y escribe BMP,
`payload/` arma e interpreta el formato oculto, `steg/` contiene los algoritmos
y `crypto/` tiene los metodos de cifrado de OpenSSL. Los tests están en `stegobmp/src/test/java/` y la consigna en `docs/`.

## Compilación y ejecución

Se requiere JDK 21, con `java` y `javac` disponibles en el `PATH`, y acceso a
Internet para la primera descarga de Maven y las dependencias. Maven Wrapper
utiliza la versión indicada en `maven-wrapper.properties`.

### Compilación

Desde la raíz del repositorio:

```shell
cd stegobmp
```

```shell
./mvnw clean package
```

Este comando compila el proyecto completo y ejecuta los tests.

### Ejecución

Para ejecutar la distribución `stegobmp.tar.gz`, desde el
directorio `stegobmp/`:

#### Shell

```shell
cd target
```

```shell
tar -xzf stegobmp.tar.gz
```

```shell
cd stegobmp
```

El script se ejecuta como `./stegobmp`. A continuación se
reproducen los ejemplos de las secciones 5.1 y 5.2 de la
[consigna](docs/Trabajo%20Practico%20Implementacion-2026_2.md), usando guiones
ASCII (`-`) y comillas rectas (`"`) para que puedan copiarse a la terminal.

**Ocultar con contraseña:** archivo `mensaje1.txt`, portador `imagen1.bmp`,
salida `imagenmas1.bmp`, algoritmo LSBI y cifrado 3DES en modo CBC.

```shell
./stegobmp -embed -in "mensaje1.txt" -p "imagen1.bmp" -out "imagenmas1.bmp" \
  -steg LSBI -a 3des -m cbc -pass "oculto"
```

**Ocultar sin contraseña:** el mismo archivo y portador, con LSBI.

```shell
./stegobmp -embed -in "mensaje1.txt" -p "imagen1.bmp" -out "imagenmas1.bmp" \
  -steg LSBI
```

**Extraer el archivo del ejemplo con contraseña:** usar el mismo algoritmo,
modo y contraseña con los que se ocultó.

```shell
./stegobmp -extract -p "imagenmas1.bmp" -out "mensaje1" \
  -steg LSBI -a 3des -m cbc -pass "oculto"
```

Los dos ejemplos de ocultamiento son alternativas: escriben la misma salida.
Las rutas de los archivos se interpretan respecto del directorio desde el que se
invoca el programa; también se pueden indicar rutas absolutas.

## Reglas del parser

- Exactamente una operación: `-embed` oculta un archivo; `-extract` lo recupera.
- `-p`, `-out` y `-steg` son obligatorios en ambas operaciones.
- `-p`: ruta del BMP portador.
- `-out`: ruta con nombre del BMP de salida al ocultar; al extraer, nombre base
  al que se agrega la extensión recuperada. La carpeta debe existir.
- `-in`: archivo a ocultar; obligatorio con `-embed` y no se admite con `-extract`.
- `-steg`: algoritmo de esteganografía: `LSB1`, `LSB4` o `LSBI`.
- `-a`: algoritmo de cifrado: `aes128`, `aes192`, `aes256` o `3des`; por defecto `aes128`.
- `-m`: modo de cifrado: `ecb`, `cfb`, `ofb` o `cbc`; por defecto `cbc`.
- `-pass`: contraseña para cifrar o descifrar.
- Solo la presencia de `-pass` habilita cifrado/descifrado en la configuración.
  Sin contraseña, `-a` y `-m` no lo habilitan. Una contraseña explícita no puede estar vacía.
- Se respetan mayúsculas y minúsculas. Se rechazan opciones desconocidas,
  opciones repetidas, valores faltantes, rutas vacías y argumentos sueltos.
- `-h` y `--help` muestran la ayuda.
