# Práctica de Laboratorio No. 2: Comunicación Serial con Java — API jSerialComm

**Sistema de Adquisición Continua Multicanal y Telecontrol Bidireccional entre Microcontrolador Simulado y Aplicación de Escritorio en Java**

* **Asignatura:** Interfaces (710044M / 710109C)
* **Docente:** Eval Bladimir Bacca Cortés, Ph.D.
* **Escuela de Ingeniería Eléctrica y Electrónica (EIEE)** — Universidad del Valle
* **Semestre:** 2026-II

---

## 1. Integrantes del Equipo y Asignación Modular (SCRUM)

| Integrante | Rol SCRUM | Carpeta Asignada | Rama de Trabajo | Responsabilidad Principal |
| :--- | :--- | :--- | :--- | :--- |
| **Danny Santiago Erazo Agredo** | Product Owner & Developer | `/laboratorio2-serial-gui` | `feature/enlace-serial-java` | Gestión de puerto virtual (`COM2`) con `jSerialComm`, parser asíncrono (`SerialPortDataListener`) y despacho de comandos. |
| **Brayan Alexis Gómez Castaño** | Scrum Master & Developer | `/firmware` | `feature/firmware-micro` | Firmware en C para el microcontrolador (ADC de 8 canales, lectura de 4 switches, Timer periódico, UART RX/TX) y binario `.hex`. |
| **Santiago Gutiérrez Reina** | UX / QA Tester & Developer | `/simulacion` | `feature/simulacion-proteus` | Esquemático en Proteus/SimulIDE (8 potenciómetros, 4 DIP-Switch, 4 LEDs, COMPIM en `COM1`), GUI Swing de conexión y QA. |

---

## 2. Arquitectura del Repositorio

```text
laboratorio2-comunicacion-serial/
│
├── simulacion/                            # Esquemático electrónico (Proteus / SimulIDE)
│   └── circuito_laboratorio2.pdsprj       # Circuito con COMPIM configurado en COM1 (9600, 8-N-1)
│
├── firmware/                              # Código embebido del microcontrolador
│   ├── src/                               # Código fuente en C (.c / .h)
│   └── bin/                               # Ejecutable compilado listo para cargar en Proteus
│       └── firmware_lab2.hex
│
└── laboratorio2-serial-gui/               # Aplicación de escritorio en Java (Maven / JDK 22)
    ├── pom.xml                            # Dependencias: jSerialComm 2.10.4 y JFreeChart 1.5.4
    └── src/main/java/com/laboratorio/
        ├── App.java                       # Punto de entrada en el Event Dispatch Thread (EDT)
        ├── controller/                    # SerialCommunicationManager y SerialConnectionException
        ├── model/                         # TramaTelemetria, persistencia tabular y estructuras
        └── view/                          # Interfaz gráfica Swing y paneles de graficación
