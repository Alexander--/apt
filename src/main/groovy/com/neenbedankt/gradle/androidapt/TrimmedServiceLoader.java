package com.neenbedankt.gradle.androidapt;

import javax.annotation.processing.Processor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

// Borrowed from java.lang.ServiceLoader (unfortunately, it neither allows
// getting names of services without loading them, nor have static cache
// that would have allowed us to carelessly pre-load them)
final class TrimmedServiceLoader implements Iterable<String> {
    private static final String PREFIX = "META-INF/services/";

    // The class or interface representing the service being loaded
    private static final Class<Processor> service = Processor.class;

    // The class loader used to locate providers
    private final ClassLoader loader;

    // Cached providers, in instantiation order
    private LinkedHashSet<String> providers = new LinkedHashSet<String>();

    // The current lazy-lookup iterator
    private LazyIterator lookupIterator;

    public void reload() {
        providers.clear();
        lookupIterator = new LazyIterator(loader);
    }

    private TrimmedServiceLoader(ClassLoader loader) {
        if (loader == null)
            throw new NullPointerException("ClassLoader instance cannot be null");

        this.loader = loader;

        reload();
    }

    private static void fail(Class<?> service, String msg, Throwable cause)
            throws ServiceConfigurationError
    {
        throw new ServiceConfigurationError(service.getName() + ": " + msg, cause);
    }

    private static void fail(Class<?> service, String msg)
            throws ServiceConfigurationError
    {
        throw new ServiceConfigurationError(service.getName() + ": " + msg);
    }

    private static void fail(Class<?> service, URL u, int line, String msg)
            throws ServiceConfigurationError
    {
        fail(service, u + ":" + line + ": " + msg);
    }

    // Parse a single line from the given configuration file, adding the name
    // on the line to the names list.
    //
    private int parseLine(Class<?> service, URL u, BufferedReader r, int lc,
                          List<String> names)
            throws IOException, ServiceConfigurationError
    {
        String ln = r.readLine();
        if (ln == null) {
            return -1;
        }
        int ci = ln.indexOf('#');
        if (ci >= 0) ln = ln.substring(0, ci);
        ln = ln.trim();
        int n = ln.length();
        if (n != 0) {
            if ((ln.indexOf(' ') >= 0) || (ln.indexOf('\t') >= 0))
                fail(service, u, lc, "Illegal configuration-file syntax");
            int cp = ln.codePointAt(0);
            if (!Character.isJavaIdentifierStart(cp))
                fail(service, u, lc, "Illegal provider-class name: " + ln);
            for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                cp = ln.codePointAt(i);
                if (!Character.isJavaIdentifierPart(cp) && (cp != '.'))
                    fail(service, u, lc, "Illegal provider-class name: " + ln);
            }
            if (!providers.contains(ln) && !names.contains(ln))
                names.add(ln);
        }
        return lc + 1;
    }

    // Parse the content of the given URL as a provider-configuration file.
    //
    // @param  service
    //         The service type for which providers are being sought;
    //         used to construct error detail strings
    //
    // @param  u
    //         The URL naming the configuration file to be parsed
    //
    // @return A (possibly empty) iterator that will yield the provider-class
    //         names in the given configuration file that are not yet members
    //         of the returned set
    //
    // @throws ServiceConfigurationError
    //         If an I/O error occurs while reading from the given URL, or
    //         if a configuration-file format error is detected
    //
    private Iterator<String> parse(Class<?> service, URL u)
            throws ServiceConfigurationError
    {
        InputStream inStr = null;
        BufferedReader r = null;
        List<String> names = new LinkedList<String>();
        try {
            inStr = u.openStream();
            r = new BufferedReader(new InputStreamReader(inStr, "utf-8"));
            int lc = 1;
            while ((lc = parseLine(service, u, r, lc, names)) >= 0);
        } catch (IOException x) {
            fail(service, "Error reading configuration file", x);
        } finally {
            try {
                if (r != null) r.close();
                if (inStr != null) inStr.close();
            } catch (IOException y) {
                fail(service, "Error closing configuration file", y);
            }
        }
        return names.iterator();
    }

    // Private inner class implementing fully-lazy provider lookup
    //
    private class LazyIterator implements Iterator<String>
    {
        ClassLoader loader;
        Enumeration<URL> configs = null;
        Iterator<String> pending = null;
        String nextName = null;

        private LazyIterator(ClassLoader loader) {
            this.loader = loader;
        }

        public boolean hasNext() {
            if (nextName != null) {
                return true;
            }
            if (configs == null) {
                try {
                    String fullName = PREFIX + service.getName();
                    configs = loader.getResources(fullName);
                } catch (IOException x) {
                    fail(service, "Error locating configuration files", x);
                }
            }
            while ((pending == null) || !pending.hasNext()) {
                if (!configs.hasMoreElements()) {
                    return false;
                }
                pending = parse(service, configs.nextElement());
            }
            nextName = pending.next();
            return true;
        }

        public String next() {
            if (!hasNext())
                throw new NoSuchElementException();
            String cn = nextName;
            nextName = null;

            // no checks for actual existence of the Processor - javac is supposed
            // to handle incorrect arguments on it's own anyway

            return cn;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public Iterator<String> iterator() {
        return new Iterator<String>() {

            Iterator<String> knownProviders = providers.iterator();

            public boolean hasNext() {
                return knownProviders.hasNext() || lookupIterator.hasNext();
            }

            public String next() {
                return knownProviders.hasNext() ? knownProviders.next() : lookupIterator.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static TrimmedServiceLoader load(ClassLoader loader)
    {
        return new TrimmedServiceLoader(loader);
    }
}
