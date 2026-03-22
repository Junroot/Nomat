interface NavigationContent extends React.PropsWithChildren {}

const Column2: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="w-full flex-1 md:flex-2 flex flex-col px-1 gap-4 min-h-0"
    >
        {children}
    </div>
}

export default Column2