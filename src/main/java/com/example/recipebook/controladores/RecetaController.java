package com.example.recipebook.controladores;

import com.example.recipebook.entidades.Receta;
import com.example.recipebook.servicios.BookService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME;

@Slf4j
// Si no queremos usar @Autowired usamos Lombok para inject
@RequiredArgsConstructor
@Controller
public class RecetaController {
    private final BookService servicio;
    private static final int COOKIE_MAX_AGE = 604800; // 7 * 24 * 60 * 60 = 604800 (7 días)
    private static final String CONTADOR_NAME_INICIO = "numVisitasIndex";
    private static final String CONTADOR_NAME_APP = "numVisitasApp";
    @GetMapping({"/","/inicio"})
    public String inicio(Model model, HttpServletRequest request, HttpServletResponse response) {
        List<Receta> listaRecetas = servicio.obtenerRecetasOrdenadasPorVisitas();


        // Actualizar las visitas para cada receta
        for (Receta receta : listaRecetas) {
            servicio.obtenerRecetaYActualizarVisitas(receta.getId());
        }
        // Obtener solo las primeras 3 recetas
        List<Receta> primerasRecetas = listaRecetas.stream().limit(3).collect(Collectors.toList());

        // Agregar las recetas a model
        model.addAttribute("Recetas", primerasRecetas);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)){
            String user = authentication.getName();

            // Si ya hay datos de contador de visitas en la sesión es que no es la primera vez
            // que se pasa por aquí y no incrementamos la cookie

            HttpSession session =  request.getSession();
            boolean primeraEntrada=(session.getAttribute(CONTADOR_NAME_APP)==null);

            // Comprobar si el navegador tenía cookie del usuario
            if (primeraEntrada){
                Optional<Cookie> cookieEncontrada = Arrays.stream(request.getCookies())
                        .filter(cookie -> user.equals(cookie.getName()))
                        .findAny();
                int contador=1;
                if (cookieEncontrada.isEmpty()){
                    Cookie cookie = new Cookie(user,"1");
                    cookie.setPath("/");
                    cookie.setDomain("localhost");
                    cookie.setMaxAge(COOKIE_MAX_AGE);
                    cookie.setSecure(true);
                    cookie.setHttpOnly(true);

                    response.addCookie(cookie);
                }else {
                    Cookie cookie = cookieEncontrada.get();
                    contador = Integer.parseInt(cookie.getValue()) + 1;
                    cookie.setValue(String.valueOf(contador));
                    cookie.setMaxAge(7 * 24 * 60 * 60);  // 7 días
                    response.addCookie(cookie);
                }
                // Almacenar en session el contador de visitas a la aplicación
                session.setAttribute(CONTADOR_NAME_APP, contador);
                log.info("id de session: {}", session.getId());




            }
            // Almacenar en session el contador de visitas a la pagina index
            Object contadorIndex = session.getAttribute(CONTADOR_NAME_INICIO);
            session.setAttribute(CONTADOR_NAME_INICIO, (contadorIndex == null) ? 1 : (int)contadorIndex + 1);
            log.info("idioma preferido: {}", session.getAttribute(LOCALE_SESSION_ATTRIBUTE_NAME));
            log.info("atributo del idioma {}", LOCALE_SESSION_ATTRIBUTE_NAME);
        }
        return "inicio";
    }

    @GetMapping({ "receta/list"})
    public String listado(Model model) {
        List<Receta> listaRecetas = servicio.obtenerListaRecetas();
        model.addAttribute("listaRecetas", listaRecetas);
        return "list";
    }

    @GetMapping({ "login/receta/list"})
    public String listadoLog(Model model) {
        List<Receta> listaRecetas = servicio.obtenerListaRecetas();
        model.addAttribute("listaRecetas", listaRecetas);
        return "list";
    }
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("receta/new")
    public String nuevaMascota(Model model) {
        log.info("Se esta agregando una nueva receta");
        model.addAttribute("recetaDto", new Receta());
        model.addAttribute("modoEdicion", false);
        return "RecetaFormulario";
    }

    @PostMapping("receta/new/submit")
    //@ModelAtribute equivaldría a esto
    //public String nuevaMascotaSubmit(Mascota nuevaMascota, Model model) {
    //    Mascota nuevaMascota = model.getAttribute("mascotaDto");
    public String nuevaRecetaSubmit(@Valid @ModelAttribute("recetaDto") Receta nuevaReceta,
                                     BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("modoEdicion", false);
            return "RecetaFormulario";
        } else if (servicio.findById(nuevaReceta.getId()) != null) {
            model.addAttribute("modoEdicion", false);
            bindingResult.rejectValue("id", "id.existente", "ya existe este id");
            return "RecetaFormulario";
        } else {
            servicio.add(nuevaReceta);
            model.addAttribute("listaRecetas", servicio.obtenerListaRecetas()); // Actualiza el modelo con la lista actualizada de recetas
            try {
                servicio.copyFile();
            } catch (IOException e) {
                // Manejar la excepción adecuadamente
                e.printStackTrace();
            }

            return "redirect:/receta/list";
        }

    }

    @GetMapping("/receta/edit/{id}")
    public String editarRecetaForm(@PathVariable int id, Model model) {

        Receta receta = servicio.findById(id);
        if (receta != null) {
            model.addAttribute("recetaDto", receta);
            model.addAttribute("modoEdicion", true);
            return "RecetaFormulario";
        } else {
            return "redirect:/receta/new";
        }
    }

    @PostMapping("/receta/edit/submit")
    public String editarRecetaSubmit(@Valid @ModelAttribute("recetaDto") Receta receta,
                                     BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("modoEdicion", true);
            return "RecetaFormulario";  // Corregir la vista aquí
        } else {
            servicio.edit(receta);
            return "redirect:/receta/list";
        }
    }
    @GetMapping("/receta/verRecetaCompleta/{id}")
    public String verRecetaCompleta(@PathVariable int id, Model model) {
        Receta receta = servicio.findById(id);
        servicio.obtenerRecetaYActualizarVisitas(id);
        model.addAttribute("recetaDto", receta);
        return "recetacompleta";

    }
    @GetMapping("receta/borrar/{id}")
    public String borrarReceta(@PathVariable("id") int id) {
        boolean borradoExitoso = servicio.borrarRecetaById(id);
        if (borradoExitoso) {
            return "redirect:/receta/list";
        } else {
            return "recetacompleta";
        }
    }

}



